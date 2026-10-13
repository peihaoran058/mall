package com.example.mall.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 * 消息在途计数器 + 消费流水（第20课时：把"异步"变成可观测、可等待）
 * ============================================================================
 * 【讲解顺序 ⑨ · 支撑】看板与实验支撑类，随堂用到再翻（见 mq 包导读）。
 *
 * <p><b>为什么需要它？</b><br>
 * 异步落库让接口响应变快了，但也带来一个演示上的致命问题：
 * <b>压测结果没法看。</b></p>
 *
 * <p>V5 压完 20 并发，库存立刻就是最终值，读回来直接判定。
 * V6/V7/V8 压完的那一瞬间，20 条消息可能还躺在队列里没被消费，
 * 此时读数据库库存会发现"怎么好像没扣？"—— 这不是 BUG，是<b>最终一致性的正常表现</b>，
 * 但课堂上没法用这个数据讲结论。</p>
 *
 * <p>所以这里维护一个"在途计数"：</p>
 * <ul>
 *     <li>{@code markSent(messageId)} —— 消息发出时 +1（注意是<b>发送时</b>，不是消费时，
 *         这样"还在队列里排队"的消息也被算作在途）；</li>
 *     <li>{@code markDone(messageId)} —— 消费者处理完（成功/失败/进死信）时 -1，必须放 finally；</li>
 *     <li>{@code awaitDrain()} —— 压测主线程等到归零再读库存，
 *         于是拿到的就是"异步链路全部跑完"之后的最终数据。</li>
 * </ul>
 *
 * <p><b>⚠️ 两个坑，都是"异步"特有的，课上值得点一下：</b></p>
 * <ol>
 *     <li><b>必须按 messageId 记账，不能简单地 +1/-1。</b>
 *         因为 Spring 的消费重试会把<b>同一个</b>监听方法再调 2 次，
 *         无脑 -1 会让在途数变成负数，压测的"排空等待"就直接误判成"已排空"。</li>
 *     <li><b>必须放 finally。</b>任何一条消息漏了 markDone，
 *         在途数永远归不了零，压测就卡到超时。</li>
 * </ol>
 *
 * <p><b>顺带澄清一个概念：</b>"异步"不等于"不用等结果"，而是"不阻塞用户请求"。
 * 等待可以发生在任何需要它的地方 —— 压测脚本要等，对账任务要等，
 * 只有<b>用户的下单请求</b>不用等。</p>
 *
 * <p>⚠️ 这个类是<b>教学观测工具</b>，只在单实例内存里计数，多实例部署下
 * 每个实例只能看到自己发出的消息。真实项目应该看 MQ 的队列积压
 * （{@code /api/demo/mq/status} 里也顺带返回了队列深度）。</p>
 */
@Slf4j
@Component
public class OrderMessageTracker {

    /** 流水最多保留多少条（防止长时间运行把内存吃光） */
    private static final int MAX_LOGS = 300;

    /** 在途（已发出但还没处理完）的消息数 */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** 累计投递（= 真的发出去了的消息数） */
    private final AtomicLong sent = new AtomicLong();
    /** 累计落库成功 */
    private final AtomicLong persisted = new AtomicLong();
    /** 累计重复消息（幂等跳过） */
    private final AtomicLong duplicate = new AtomicLong();
    /** 累计业务失败（库存不足，消息被正常 ack） */
    private final AtomicLong stockNotEnough = new AtomicLong();
    /** 累计系统异常（抛异常 → 重试 → 重试耗尽进死信） */
    private final AtomicLong error = new AtomicLong();
    /** 累计触发 DB 兜底并回补了 Redis 库存 */
    private final AtomicLong compensated = new AtomicLong();
    /** 累计被入口（Redis 预扣）挡掉、压根没投递的请求 */
    private final AtomicLong gateRejected = new AtomicLong();

    /** 已发出但未处理完的 messageId（用来把 markDone 做成幂等） */
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    /** 已记过异常的 messageId（重试会让同一条消息多次抛异常，只记一次） */
    private final Set<String> errorKeys = ConcurrentHashMap.newKeySet();

    /** 消费流水（最新在前），给前端实验台展示 */
    private final Deque<String> logs = new ConcurrentLinkedDeque<>();

    /* ==================== 记账 ==================== */

    /** 消息发出：+1 在途。必须在真正 convertAndSend 之前调用 */
    public void markSent(String messageId) {
        sent.incrementAndGet();
        if (messageId != null && pending.add(messageId)) {
            inFlight.incrementAndGet();
        }
    }

    /** 消息处理完：-1 在途。按 messageId 幂等，重试不会重复扣减 */
    public void markDone(String messageId) {
        if (messageId != null) {
            if (pending.remove(messageId)) {
                inFlight.decrementAndGet();
            }
        } else {
            inFlight.decrementAndGet();
        }
    }

    /** 落库成功 */
    public void markPersisted() {
        persisted.incrementAndGet();
    }

    /** 重复消息，幂等跳过 */
    public void markDuplicate() {
        duplicate.incrementAndGet();
    }

    /** 业务失败：库存不足（消息被正常 ack，不重试） */
    public void markStockNotEnough() {
        stockNotEnough.incrementAndGet();
    }

    /** 系统异常（会触发重试；同一条消息只计一次） */
    public void markError(String messageId) {
        boolean first = messageId == null || errorKeys.add(messageId);
        if (first) {
            error.incrementAndGet();
        }
    }

    /** DB 兜底生效并把库存回补给了 Redis */
    public void markCompensated() {
        compensated.incrementAndGet();
    }

    /** 被入口的 Redis 预扣挡掉（V8 专属：请求根本没进队列） */
    public void markGateRejected() {
        gateRejected.incrementAndGet();
    }

    /* ==================== 消费流水 ==================== */

    /** 记一条消费流水（最新在前） */
    public void record(String line) {
        if (line == null) {
            return;
        }
        logs.addFirst(line);
        while (logs.size() > MAX_LOGS) {
            logs.pollLast();
        }
    }

    /** 取流水快照（最新在前） */
    public List<String> logs() {
        return new ArrayList<>(logs);
    }

    /* ==================== 等待排空 ==================== */

    /**
     * 等所有在途消息处理完。
     *
     * @return true = 已排空；false = 等待超时仍有在途（压测数据可能不完整，返回值里会标出来）
     */
    public boolean awaitDrain(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        while (System.currentTimeMillis() < deadline) {
            if (inFlight.get() <= 0) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        boolean drained = inFlight.get() <= 0;
        if (!drained) {
            log.warn("【MQ】等待落库排空超时 | 仍有 {} 条在途消息", inFlight.get());
        }
        return drained;
    }

    /** 当前在途数 */
    public int inFlight() {
        return Math.max(0, inFlight.get());
    }

    /** 消费完成数 = 落库成功 + 幂等跳过 + 业务失败 + 系统异常 */
    public long consumed() {
        return persisted.get() + duplicate.get() + stockNotEnough.get() + error.get();
    }

    /* ==================== 生命周期 ==================== */

    /** 演示每一轮开始前清零，保证统计是"本轮"的 */
    public void reset() {
        inFlight.set(0);
        sent.set(0);
        persisted.set(0);
        duplicate.set(0);
        stockNotEnough.set(0);
        error.set(0);
        compensated.set(0);
        gateRejected.set(0);
        pending.clear();
        errorKeys.clear();
        logs.clear();
    }

    /** 给接口返回用的快照 */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>(16);
        data.put("inFlight", inFlight());
        data.put("sent", sent.get());
        data.put("consumed", consumed());
        data.put("persisted", persisted.get());
        data.put("duplicate", duplicate.get());
        data.put("stockNotEnough", stockNotEnough.get());
        data.put("error", error.get());
        data.put("compensated", compensated.get());
        data.put("gateRejected", gateRejected.get());
        return data;
    }
}
