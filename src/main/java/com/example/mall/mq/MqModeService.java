package com.example.mall.mq;

import com.example.mall.config.MallProperties;
import com.example.mall.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 * 秒杀异步下单的"分发开关"（第20课时）
 * ============================================================================
 * 【讲解顺序 ① · 公共开关】第一节课先打开这个类：6/7/8 怎么切、切了以后
 * 入口和消费端谁变谁不变，一张表说清 —— 后面所有类都挂在它分的三个版本上。
 *
 * <p>和 {@code OrderOversellService}（{@code mall.order.version} 1~5）、
 * {@code OrderTxSubmitService}（{@code mall.order.tx-version} 1~5）
 * 是同一个套路的三个开关，<b>互不干扰，可以同时存在</b>：</p>
 *
 * <pre>
 * mall.order.version      1~5   并发超卖专题（第 13~16 课时）→ 购物车同步下单
 * mall.order.tx-version   1~5   事务失效专题（第 10~11 课时）
 * mall.mq.mode            6~8   秒杀异步下单（第 20 课时）★ 本类 → /seckill 页
 * </pre>
 *
 * <table border="1">
 *     <caption>版本对照（与前端 MQ 实验台的卡片一一对应）</caption>
 *     <tr><th>版本</th><th>入口（生产者）</th><th>消费端</th><th>洪峰预期</th></tr>
 *     <tr><td>6</td><td>直接投递，不拦</td>
 *         <td>读库存 → Java 判断 → <b>无条件扣减</b></td>
 *         <td>❌ 超卖（库存负数）</td></tr>
 *     <tr><td>7</td><td>直接投递，不拦</td>
 *         <td>SETNX 幂等 → <b>带条件的原子扣减</b> → 落库</td>
 *         <td>✅ 不超卖，但洪峰全量进队列</td></tr>
 *     <tr><td>8</td><td><b>Redis Lua 预扣</b>，超出的请求直接拒绝</td>
 *         <td>SETNX 幂等 → 原子扣减 → 落库；DB 拦下则回补 Redis</td>
 *         <td>✅ 不超卖，且绝大多数请求根本进不了队列</td></tr>
 * </table>
 *
 * <p><b>为什么"入口要不要预扣"也由这个开关决定？</b><br>
 * 因为 V7 和 V8 的差别<b>只在入口</b>：消费端两版完全一样。
 * 把两件事放在同一个开关里，课堂上切一下就能看到"消费端不变、
 * 只是把门禁前移"带来的差别 —— 这比切两个开关清楚得多。
 * （代价是这个开关要同时被生产者和消费者读到，所以它必须是一个独立的 Bean，
 * 而不是某个服务类里的私有字段。）</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqModeService {

    /** V6：只有 MQ，消费端无并发保护 */
    public static final int MODE_MQ_ONLY = 6;
    /** V7：MQ + 幂等 + 数据库原子扣减 */
    public static final int MODE_MQ_ATOMIC = 7;
    /** V8：最终版，入口 Redis Lua 预扣 + MQ 异步落库 + 数据库兜底 */
    public static final int MODE_REDIS_PRE_DEDUCT = 8;

    private final MallProperties mallProperties;

    /**
     * 运行期覆盖值（课堂热切换用，不用重启）。
     * <p>{@code null} = 跟随 yml 里的 {@code mall.mq.mode}。
     * 用 {@code volatile} 保证请求线程改了之后，MQ 消费线程立刻能看到 ——
     * 消费线程和我们不是同一个线程，这里不加 volatile 就可能出现
     * "前端显示已切到 V7，消费端还在按 V6 跑"的诡异现象。</p>
     */
    private volatile Integer runtimeMode = null;

    /** 当前生效的版本：优先运行期覆盖，其次 yml（yml 没配默认 V7） */
    public int current() {
        Integer override = this.runtimeMode;
        if (override != null) {
            return override;
        }
        Integer configured = mallProperties.getMq().getMode();
        return configured == null ? MODE_MQ_ATOMIC : configured;
    }

    /** 当前 yml 里配的值（前端要展示"yml 配的是 V几"） */
    public Integer ymlMode() {
        return mallProperties.getMq().getMode();
    }

    /** 运行期覆盖值，null = 没覆盖 */
    public Integer runtimeMode() {
        return this.runtimeMode;
    }

    /** 入口是否要做 Redis 预扣（V8 专属） */
    public boolean preDeductEnabled() {
        return current() >= MODE_REDIS_PRE_DEDUCT;
    }

    /** 消费端是否启用幂等 + 原子扣减（V7/V8） */
    public boolean atomicPersistEnabled() {
        return current() >= MODE_MQ_ATOMIC;
    }

    /**
     * 运行期切换版本：value = 6/7/8（实验版本） / 0（清除覆盖，回到 yml 配置）。
     */
    public Map<String, Object> set(Integer value) {
        int v = value == null ? 0 : value;
        if (v != 0 && (v < MODE_MQ_ONLY || v > MODE_REDIS_PRE_DEDUCT)) {
            throw new BusinessException("value 只能是 0（清除覆盖）或 6/7/8（实验版本）");
        }
        this.runtimeMode = v == 0 ? null : v;

        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("mode", current());
        data.put("modeOverride", this.runtimeMode);
        data.put("ymlMode", ymlMode());
        data.put("description", describe(current()));
        data.put("message", v == 0
                ? "已清除运行期覆盖，回到 application.yml 的 mall.mq.mode"
                : "已切换到 V" + v + "，下一轮洪峰实验和秒杀下单就按这一版执行");
        log.info("【课堂实验】切换 MQ 消费端版本 → V{} | {}", current(), describe(current()));
        return data;
    }

    /** 版本说明，给接口返回和日志用 */
    public String describe(int mode) {
        switch (mode) {
            case MODE_MQ_ONLY:
                return "V6 只有 MQ：入口直接投递，消费端读库存 → Java 判断 → 无条件扣减，并发消费时依然超卖"
                        + "（结论：MQ 解决削峰，不解决超卖）";
            case MODE_MQ_ATOMIC:
                return "V7 MQ + 幂等 + 原子扣减：消费端用 Redis SETNX 挡住重复投递，"
                        + "并用 UPDATE ... WHERE stock >= n 把判断和扣减合成一步，由行锁保证不超卖";
            case MODE_REDIS_PRE_DEDUCT:
                return "V8 最终版：入口 Redis Lua 原子预扣（挡住绝大多数请求，不发消息、不占 DB 连接）"
                        + " + MQ 异步落库 + 数据库条件 UPDATE 兜底（DB 拦下则回补 Redis）";
            default:
                return "未知版本：" + mode;
        }
    }
}
