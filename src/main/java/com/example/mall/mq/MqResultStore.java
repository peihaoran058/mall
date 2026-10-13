package com.example.mall.mq;

import com.example.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * 秒杀结果暂存（第20课时：异步下单的"最后一公里"）
 * ============================================================================
 * 【讲解顺序 ⑨ · 支撑】看板与实验支撑类，随堂用到再翻（见 mq 包导读）。
 *
 * <p><b>为什么需要它？</b><br>
 * 秒杀接口返回的是"已进入队列"，<b>不是最终结果</b>。
 * 用户点完秒杀之后怎么知道到底买没买到？——必须有一个地方能查到结果。
 * 很多项目上了 MQ 却忘了做结果回查，用户点完就一脸茫然，
 * 体验比同步下单还差。这个类就是补上这一环。</p>
 *
 * <p><b>为什么用内存 Map 而不是 Redis？</b><br>
 * 教学项目单实例部署，内存足够，而且少一个依赖、少一层调试成本。
 * 真实项目应该写 Redis（多实例共享 + 天然 TTL），
 * 或者更彻底一点：<b>直接查订单表</b> —— 因为订单落库之后，
 * "订单存不存在"本身就是最权威的结果。</p>
 *
 * <p>本类的 {@link #get} 返回 {@code null} 时，调用方（{@link OrderMqService#result}）
 * 会去查一次数据库做兜底，两个来源合起来才能覆盖所有情况：</p>
 * <ul>
 *     <li>落库成功但 Map 被清过 / 服务重启过 → 靠查库拿到 SUCCESS；</li>
 *     <li>库存不足（压根没落库）→ 只能靠本类记的 FAILED；</li>
 *     <li>还在队列里 → 两边都查不到 → 回答 QUEUING。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqResultStore {

    private final MallProperties mallProperties;

    private final Map<String, MqOrderResult> store = new ConcurrentHashMap<>();

    /** 写入 / 覆盖一条结果 */
    public void put(MqOrderResult result) {
        if (result == null || result.getOrderNo() == null) {
            return;
        }
        result.setUpdatedAt(System.currentTimeMillis());
        store.put(result.getOrderNo(), result);
        evictExpired();
    }

    /** 读一条结果，没有返回 null */
    public MqOrderResult get(String orderNo) {
        if (orderNo == null) {
            return null;
        }
        MqOrderResult result = store.get(orderNo);
        if (result == null) {
            return null;
        }
        if (System.currentTimeMillis() - result.getUpdatedAt() > ttlMillis()) {
            store.remove(orderNo);
            return null;
        }
        return result;
    }

    /** 清空（实验台"统计归零"时一起清） */
    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    /* ==================== 内部 ==================== */

    private long ttlMillis() {
        Long seconds = mallProperties.getMq().getResultTtlSeconds();
        return (seconds == null ? 600L : seconds) * 1000L;
    }

    /**
     * 惰性清理过期结果。
     * <p>没有额外起定时任务：每次写入时顺手扫一遍，够用且不会引入
     * "定时任务和业务抢锁"这类新问题。压测场景下 Map 最多几百条，扫描成本可忽略。</p>
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        long ttl = ttlMillis();
        store.entrySet().removeIf(e -> now - e.getValue().getUpdatedAt() > ttl);
    }
}
