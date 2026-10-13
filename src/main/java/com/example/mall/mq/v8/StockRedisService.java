package com.example.mall.mq.v8;

import com.example.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * 【V8 新增】Redis 可售库存（秒杀门禁）—— Lua 原子预扣 / 回补 / 预热
 * ============================================================================
 * 【讲解顺序 ⑦】最后一个版本只讲入口：V8 消费端与 V7 一字不差，差别全在这。
 *
 * <p><b>它在整个链路里的位置：</b></p>
 * <pre>
 *   用户请求 ──> ① Redis Lua 原子预扣（本类）   ← 只有这里能"放行"，扛住全部并发
 *                    │ 扣成功
 *                    ▼
 *               ② 发 MQ 消息（削峰）
 *                    │
 *                    ▼
 *               ③ 消费者异步落库 + DB 兜底（最终一致）
 * </pre>
 *
 * <p><b>为什么不直接把库存放在数据库里？</b><br>
 * V5 的 {@code UPDATE ... WHERE stock >= n} 已经不会超卖了，但它把全部并发
 * 直接压在 MySQL 行锁上：1 万个请求排队等同一行的锁，大量连接被占住、
 * 响应时间飙升，数据库这个"最贵的资源"成了整个系统的天花板。
 * 把库存判断挪到 Redis（内存 + 单线程），绝大多数无效请求在 Redis 就被挡掉，
 * 只有真正扣成功的少量请求才需要落库 —— 这就是"削峰填谷"。</p>
 *
 * <p><b>代价（课堂必须讲清，否则学生会以为这是银弹）：</b></p>
 * <ul>
 *     <li>多了一层数据，<b>一致性变复杂</b>：Redis 扣了、DB 落库失败怎么办？
 *         → 消费者里做补偿（回补 Redis 库存），另外还需要定时对账兜底。</li>
 *     <li>Redis 重启 / 数据丢失 → 库存数据没了 → 必须有预热 + 对账机制，
 *         否则就是脚本里那个 -2（未预热）状态。</li>
 *     <li>集群部署时 Redis 主从切换可能丢数据，要接受"宁可少卖不可超卖"的原则。</li>
 * </ul>
 *
 * <p>核心原则一句话：<b>数据库是最后一道防线，不是第一道门。</b>
 * Redis 负责"快"，数据库负责"准"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisService {

    /** 预扣失败：库存不足（业务上正常的结果） */
    public static final long RESULT_INSUFFICIENT = -1L;

    /** 预扣失败：库存未预热（故障状态，必须显式提示，不能当成卖完了） */
    public static final long RESULT_NOT_WARMED = -2L;

    /**
     * 预扣脚本从 classpath 的 lua/ 目录加载（src/main/resources/lua/stock_deduct.lua）。
     * <p>为什么不写成 Java 字符串常量？脚本本身要讲、要改、要对照 redis-cli 调试，
     * 单独一个 .lua 文件比嵌在字符串拼接里可读性高得多。</p>
     * <p>{@code DefaultRedisScript} 内部会先算 SHA1 用 EVALSHA 调用，
     * 脚本没缓存过时自动降级 EVAL 并缓存 —— 省掉每次传输脚本正文的开销。</p>
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();

    /**
     * 回补脚本：lua/stock_restore.lua。
     * <p>为什么回补也要走 Lua？不是因为竞态（INCRBY 单命令本身原子），
     * 而是因为 INCRBY 在 key 不存在时会<b>从 0 创建 key</b> —— Redis 数据丢失后
     * 一笔回补就会凭空造出假库存，把"未预热"故障掩盖掉。脚本里先 EXISTS 检查，
     * 数据不在就返回 -2 拒绝补偿，让调用方告警。</p>
     */
    private static final DefaultRedisScript<Long> RESTORE_SCRIPT = new DefaultRedisScript<>();

    static {
        DEDUCT_SCRIPT.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        DEDUCT_SCRIPT.setResultType(Long.class);
        RESTORE_SCRIPT.setLocation(new ClassPathResource("lua/stock_restore.lua"));
        RESTORE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final MallProperties mallProperties;

    /** 拼 Redis 库存 key：mall:stock:product:{productId} */
    public String stockKey(Long productId) {
        return mallProperties.getStock().getKeyPrefix() + productId;
    }

    /**
     * 【核心】原子预扣库存。
     *
     * @return {@code >= 0} 预扣成功，值为剩余库存；
     *         {@link #RESULT_INSUFFICIENT} 库存不足；
     *         {@link #RESULT_NOT_WARMED} 未预热
     */
    public long tryDeduct(Long productId, int quantity) {
        Long result = stringRedisTemplate.execute(
                DEDUCT_SCRIPT,
                Collections.singletonList(stockKey(productId)),
                String.valueOf(quantity));
        if (result == null) {
            // 脚本返回 null 只可能是连接/序列化层面的异常，按"未预热"处理并告警
            log.error("【MQ】Lua 预扣脚本返回空 | productId={} | 检查 Redis 连接", productId);
            return RESULT_NOT_WARMED;
        }
        return result;
    }

    /**
     * 回补库存（补偿操作）。
     *
     * <p>什么时候用：Redis 预扣成功了，但异步落库时数据库兜底拦下来
     * （说明 Redis 和 DB 的库存对不上），这时候必须把 Redis 多扣的那件加回去，
     * 否则这件商品就"永久少卖一件"。</p>
     *
     * <p>⚠️ 两个约束：</p>
     * <ol>
     *     <li><b>只允许在数据库事务之外调用</b>：Redis 不受事务管辖，
     *         事务里改完 Redis 又回滚，就会出现"Redis 加了库存、数据库当没发生"的双写不一致。</li>
     *     <li><b>调用方必须先抢"补偿权"</b>（见 {@code MqIdempotentGuard.tryMarkCompensated}）：
     *         同一条消息重投时如果每次都补，库存会被凭空加回来 —— 补偿过度就是另一种形式的超卖。</li>
     * </ol>
     *
     * @return 回补后的库存；{@link #RESULT_NOT_WARMED} 表示库存 key 不存在（数据丢失），
     *         此时<b>拒绝补偿</b>并应立即告警 —— 绝不能凭空创建库存把故障掩盖掉
     */
    public long restore(Long productId, int quantity) {
        Long after = stringRedisTemplate.execute(
                RESTORE_SCRIPT,
                Collections.singletonList(stockKey(productId)),
                String.valueOf(quantity));
        if (after == null || after == RESULT_NOT_WARMED) {
            log.error("【MQ】回补被拒绝：Redis 库存 key 不存在（数据丢失/未预热），"
                    + "本次补偿未执行，需要人工介入 | productId={} | +{}", productId, quantity);
            return RESULT_NOT_WARMED;
        }
        log.warn("【MQ】回补 Redis 库存 | productId={} | +{} | 回补后={}", productId, quantity, after);
        return after;
    }

    /** 预热：把数据库的库存灌进 Redis（压测/演示前必做，也是上线时的初始化动作） */
    public void warmup(Long productId, int stock) {
        stringRedisTemplate.opsForValue().set(stockKey(productId), String.valueOf(stock));
        log.info("【MQ】预热 Redis 库存 | key={} | stock={}", stockKey(productId), stock);
    }

    /**
     * 预热并设置过期时间。
     * <p>真实项目里秒杀库存通常带 TTL（活动结束自动清理），
     * 但 TTL 一到库存就"消失"（变成未预热 -2），必须配合对账任务续期或重建。</p>
     */
    public void warmup(Long productId, int stock, long ttlSeconds) {
        stringRedisTemplate.opsForValue()
                .set(stockKey(productId), String.valueOf(stock), ttlSeconds, TimeUnit.SECONDS);
        log.info("【MQ】预热 Redis 库存 | key={} | stock={} | ttl={}s", stockKey(productId), stock, ttlSeconds);
    }

    /** 读当前 Redis 库存；key 不存在返回 null（区别于"库存为 0"） */
    public Long getStock(Long productId) {
        String value = stringRedisTemplate.opsForValue().get(stockKey(productId));
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("【MQ】Redis 库存值不是数字 | key={} | value={}", stockKey(productId), value);
            return null;
        }
    }

    /** 清掉 Redis 库存 key（演示收尾用，清完就是"未预热"状态） */
    public boolean clear(Long productId) {
        Boolean deleted = stringRedisTemplate.delete(stockKey(productId));
        log.info("【MQ】清除 Redis 库存 | key={} | deleted={}", stockKey(productId), deleted);
        return Boolean.TRUE.equals(deleted);
    }
}
