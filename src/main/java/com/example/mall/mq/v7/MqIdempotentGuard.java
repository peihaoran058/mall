package com.example.mall.mq.v7;

import com.example.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * 【V7 新增 · V7/V8 共用】消息幂等闸门
 * ============================================================================
 * 【讲解顺序 ⑥】和 OrderPersistV7Service 一起讲：SETNX 挡重复投递（第一层），DB selectById 兜底（第二层）。
 *
 * <p><b>为什么必须有它？</b><br>
 * RabbitMQ 的投递语义是 <b>at-least-once（至少一次）</b>，不是 exactly-once：
 * 消费者处理完了但 ack 在网络里丢了、发送方 confirm 超时后重发、
 * 节点重启导致未 ack 的消息重回队列 —— 这些都会让<b>同一条消息被消费两次</b>。</p>
 *
 * <p>对下单接口来说，"同一条消息被消费两次"等于<b>一次下单变成两笔订单</b>，
 * 用户只点了一次却扣了两次库存。所以必须做幂等，而且要在最前面做。</p>
 *
 * <p><b>怎么做的：Redis {@code SET key value NX EX} 抢消息 id。</b></p>
 * <pre>
 *   SET mall:mq:idem:{messageId} 1 NX EX 600
 *      返回 true  → 第一次见到这条消息，放行
 *      返回 false → 已经处理过了，直接丢弃（业务上是"成功"的）
 * </pre>
 * <p>一条命令同时做到"原子占坑 + 自动过期"，不需要额外的去重表，
 * 也不用担心 key 无限增长（TTL 到期自动清理）。</p>
 *
 * <p><b>⚠️ 两个容易做错的地方：</b></p>
 * <ol>
 *     <li><b>必须在业务成功"之前"占坑，但失败时要释放。</b>
 *         如果只在业务成功后占坑，两条并发重投会同时进来；
 *         而如果在失败时不释放，Spring 的重试就会全部被自己的坑挡住 ——
 *         消息看起来"处理过了"，实际一条订单都没落，这是更隐蔽的丢单。</li>
 *     <li><b>幂等键用 messageId，不要用 orderNo。</b>
 *         messageId 标识"一条消息"，orderNo 标识"一笔业务"。
 *         V8 里入口会把同一个 orderNo 重发（网络重试），
 *         而"数据库里已经有了这个订单"由消费者里的 selectById 兜住 ——
 *         两层防线管的是两件不同的事。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqIdempotentGuard {

    /** 幂等键前缀：mall:mq:idem:{messageId} */
    public static final String IDEM_PREFIX = "mall:mq:idem:";

    /**
     * 补偿幂等键前缀：mall:mq:compensated:{messageId}。
     * <p>和消息幂等键分开是刻意的：两者生命周期不同 ——
     * 消息幂等键在业务<b>抛异常</b>时要释放（让重试能进来），
     * 而补偿标记<b>一旦补偿过就永不能重来</b>（重放消息也不许再补一次）。
     * 共用一个 key 会出现"补偿过 → 释放 → 重试 → 再补偿"的多补链路。</p>
     */
    public static final String COMPENSATE_PREFIX = "mall:mq:compensated:";

    private final StringRedisTemplate stringRedisTemplate;
    private final MallProperties mallProperties;

    /** 拼幂等键 */
    public String idemKey(String messageId) {
        return IDEM_PREFIX + messageId;
    }

    /**
     * 抢占这条消息的处理权。
     *
     * @return true = 抢到了（第一次见，继续处理）；false = 别人已经处理过（重复消息，直接丢弃）
     */
    public boolean tryAcquire(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            // 没带 id 的老消息无法幂等，放行并告警 —— 绝不能因为"没法去重"就把业务整个卡死
            log.warn("【MQ】消息没有 messageId，无法幂等，直接放行");
            return true;
        }
        Long ttl = mallProperties.getMq().getIdempotentTtlSeconds();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idemKey(messageId), "1", ttl == null ? 600L : ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放处理权（业务处理抛异常时调用）。
     *
     * <p>不释放的后果很具体：Spring 的重试第 2 次进来时，
     * SETNX 发现坑还占着 → 判定为重复消息 → 直接 ack 掉。
     * 于是这条消息<b>永远不会被真正处理</b>，而且日志里只看到"幂等跳过"。</p>
     */
    public void release(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.delete(idemKey(messageId));
        } catch (Exception e) {
            // 释放失败不算致命：TTL 到期后会自然清理，代价只是 ttl 内的重投会被当成重复
            log.warn("【MQ】释放幂等键失败（TTL 到期后会自然清理）| messageId={} | {}", messageId, e.getMessage());
        }
    }

    /**
     * 抢占"补偿权"：同一 messageId 只允许回补一次。
     *
     * <p><b>为什么补偿必须单独做幂等？</b><br>
     * 消息幂等闸门（{@link #tryAcquire}）的 key 有 TTL，且业务抛异常时会被释放；
     * 而补偿发生在"DB 兜底拦下"这个失败分支里 —— 如果这条消息在 TTL 过期后
     * 被重放（死信人工重投、broker 重启重投未 ack 消息），闸门已经不在了，
     * 消费端会再次走到失败分支。<b>不挡住的话，每重放一次就凭空多补一件库存，
     * 补偿过度就是另一种形式的超卖。</b>所以补偿权用独立的 key 抢占，抢过一次就不再补。</p>
     *
     * @return true = 第一次补偿（放行）；false = 已经补偿过，禁止再补
     */
    public boolean tryMarkCompensated(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            // 没有 id 就无法标记重放 —— 按宁可少卖原则：拒绝补偿并告警，绝不赌运气
            log.warn("【MQ】消息没有 messageId，无法标记补偿权，拒绝补偿（宁可少卖）");
            return false;
        }
        Long ttl = mallProperties.getMq().getIdempotentTtlSeconds();
        Boolean marked = stringRedisTemplate.opsForValue()
                .setIfAbsent(COMPENSATE_PREFIX + messageId, "1",
                        ttl == null ? 600L : ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(marked);
    }
}
