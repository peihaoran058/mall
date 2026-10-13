package com.example.mall.mq;

import com.example.mall.mq.v6.OrderPersistV6Service;
import com.example.mall.mq.v7.MqIdempotentGuard;
import com.example.mall.mq.v7.OrderPersistV7Service;
import com.example.mall.mq.v8.StockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * 订单落库消费者（第20课时：异步链路的执行者，也是版本差异的集中地）
 * ============================================================================
 * 【讲解顺序 ⑧ · 消费端】唯一的版本分岔口：一个 if/else 决定走 v6 还是 v7。
 * 讲到这个类时，V6/V7/V8 的入口和落库都已讲过，这里做串讲收尾。
 *
 * <p><b>它为什么存在？</b><br>
 * 秒杀场景下，用户请求在入口<b>立刻返回"抢到了"</b>，
 * 落库这件事被丢给消费者慢慢做。于是：</p>
 * <ul>
 *     <li>用户感知的响应时间只剩一次投递（几毫秒），体验好；</li>
 *     <li>数据库的写入压力变成"匀速消费"，不再受洪峰冲击（削峰填谷）；
 *         洪峰过后队列积压的消息会被 {@code max-concurrency=10} 的消费者线程池快速消化。</li>
 * </ul>
 *
 * <p><b>按 {@code mall.mq.mode} 分发，这里就是 V6/V7/V8 的分岔口：</b></p>
 * <pre>
 *   V6 ──> v6.OrderPersistV6Service                    读库存 → Java 判断 → 无条件扣减   ❌ 超卖
 *   V7 ──> v7 幂等闸门 ──> v7.OrderPersistV7Service    原子扣减 → 落库                  ✅ 不超卖
 *   V8 ──> v7 幂等闸门 ──> v7.OrderPersistV7Service + DB 拦下则回补 Redis（v8）          ✅ 不超卖
 * </pre>
 * <p>注意 V7 和 V8 的<b>消费端完全一样</b>，差别只在入口要不要先做 Redis 预扣
 * （见 {@link OrderMqService}）。这样切开关时学生的注意力会落在"门禁前移"
 * 这一件事上，而不是被一堆同时变化的代码搞晕。</p>
 *
 * <p><b>这个类和 Spring 提供的三个机制配合工作，才能"不丢消息"：</b></p>
 *
 * <table border="1">
 *     <caption>异常处理链路</caption>
 *     <tr><th>环节</th><th>机制</th><th>配置位置</th></tr>
 *     <tr><td>消费抛异常</td><td>重试 3 次（间隔 200ms、400ms 递增）</td>
 *         <td>application.yml → listener.simple.retry</td></tr>
 *     <tr><td>重试仍失败</td><td>不重回队列，转投死信队列</td>
 *         <td>default-requeue-rejected=false + 队列的 x-dead-letter-exchange</td></tr>
 *     <tr><td>人工排查</td><td>看死信队列积压</td>
 *         <td>GET /api/demo/mq/status 或 RabbitMQ 管理界面</td></tr>
 * </table>
 *
 * <p><b>⚠️ 千万不要在 catch 里"吞掉"异常</b>：一旦吞掉，Spring 认为处理成功，
 * 会正常 ack，这条消息就永远消失了 —— 业务上表现为"用户明明抢到了，却没有订单"，
 * 而且日志里什么都看不到。要么让它抛出去触发重试，要么明确记一条补偿日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMqConsumer {

    private final OrderPersistV6Service persistV6;      // 【V6】无保护落库（错误示范）
    private final OrderPersistV7Service persistV7;      // 【V7/V8】幂等 + 原子扣减落库
    private final StockRedisService stockRedisService;  // 【V8】回补 Redis / 读展示库存
    private final MqIdempotentGuard idempotentGuard;    // 【V7/V8】SETNX 幂等闸门
    private final MqModeService mqModeService;
    private final OrderMessageTracker tracker;
    private final MqResultStore resultStore;

    /**
     * 监听落库队列。
     * <p>队列名走配置 {@code mall.mq.order-queue}，和生产者用的是同一个 key，
     * 改拓扑时两边一起改，不会出现"发到 A 队列、监听 B 队列"这种低级错位。</p>
     * <p>消费并发 / 预取不写在这里，而是走 application.yml 的
     * {@code spring.rabbitmq.listener.simple.*}（引用 {@code mall.mq.*} 的值），
     * 这样同一份配置既驱动监听容器，又给前端实验台展示。</p>
     */
    @RabbitListener(queues = "${mall.mq.order-queue}")
    public void onOrderCreate(OrderCreateMessage message) {
        long start = System.currentTimeMillis();
        int mode = mqModeService.current();
        String messageId = message.getMessageId();
        String orderNo = message.getOrder() == null ? null : message.getOrder().getOrderNo();
        boolean idemAcquired = false;

        if (message.getMode() != null && message.getMode() != mode) {
            // 消息在投递后、消费前被切了版本：留痕，方便课堂上解释"同一条消息为什么表现不一样"
            log.info("【MQ】消息投递时是 V{}，现在生效的是 V{}（切换发生在飞行途中）| {}",
                    message.getMode(), mode, message.describe());
        }
        log.info("【MQ】消费者收到落库消息 | V{} | {}", mode, message.describe());

        try {
            /* ---------- ① 幂等闸门：V7/V8 才有（V6 故意不加，用来演示重复下单） ---------- */
            if (mqModeService.atomicPersistEnabled()) {
                idemAcquired = idempotentGuard.tryAcquire(messageId);
                if (!idemAcquired) {
                    tracker.markDuplicate();
                    tracker.record("⊘ 幂等拦截（重复消息）| orderNo=" + orderNo);
                    // 重复消息在业务上算"已经处理过"，结果沿用上一次的 SUCCESS，不改成失败
                    resultStore.put(MqOrderResult.success(orderNo, mode,
                            currentStock(message.getProductId()), orderIdOf(message)));
                    return;
                }
            }

            /* ---------- ② 落库：V6 走 v6 无保护版，V7/V8 走 v7 原子版 ---------- */
            OrderPersistResult result = mode == MqModeService.MODE_MQ_ONLY
                    ? persistV6.persistWithoutProtection(message)
                    : persistV7.persistAtomically(message);

            /* ---------- ③ 按结果记账 + 写结果缓存 ---------- */
            switch (result) {
                case PERSISTED:
                    tracker.markPersisted();
                    tracker.record("✓ 落库成功  orderNo=" + orderNo + "  " + elapsed(start) + "ms");
                    resultStore.put(MqOrderResult.success(orderNo, mode,
                            currentStock(message.getProductId()), orderIdOf(message)));
                    log.info("【MQ】订单落库成功 | orderNo={} | 耗时 {}ms | {}",
                            orderNo, System.currentTimeMillis() - start, message.describe());
                    break;

                case DUPLICATE:
                    tracker.markDuplicate();
                    tracker.record("⊘ 幂等拦截（DB 已存在同 id 订单）| orderNo=" + orderNo);
                    resultStore.put(MqOrderResult.success(orderNo, mode,
                            currentStock(message.getProductId()), orderIdOf(message)));
                    break;

                case STOCK_NOT_ENOUGH:
                default:
                    /*
                     * ★ V8 专属补偿：回补 Redis 库存
                     *
                     * Redis 预扣已经把这件扣掉了，但数据库兜底说"其实没货"，
                     * 说明 Redis 和 DB 的库存对不上（预热时算错、Redis 被人手工改过……）。
                     * 如果不还回去，这件商品就"永久少卖一件"。
                     *
                     * ⚠️ 补偿有三条纪律（缺一不可）：
                     *  1) 先抢"补偿权"（tryMarkCompensated）：同一条消息重放时只补一次 ——
                     *     不挡住的话，死信重放 / broker 重投每来一次就凭空多补一件，补偿过度即超卖；
                     *  2) 必须放在数据库事务【外面】：上面的 persist 方法已经返回，
                     *     事务已经结束（要么提交了、要么根本没扣）。顺序反了就是双写不一致；
                     *  3) restore 返回 -2（Redis 库存数据丢了）时拒绝补偿并告警 ——
                     *     绝不能在数据丢失时凭空创建库存，把"未预热"故障掩盖掉。
                     */
                    if (mode >= MqModeService.MODE_REDIS_PRE_DEDUCT) {
                        if (idempotentGuard.tryMarkCompensated(messageId)) {
                            long after = stockRedisService.restore(
                                    message.getProductId(), message.getQuantity());
                            if (after != StockRedisService.RESULT_NOT_WARMED) {
                                tracker.markCompensated();
                                tracker.record("↺ 已回补 Redis 库存  orderNo=" + orderNo);
                            } else {
                                tracker.record("✗ 回补被拒绝（Redis 库存数据丢失）  orderNo=" + orderNo);
                            }
                        } else {
                            tracker.record("⊘ 重复补偿被拦截（该消息已补偿过）  orderNo=" + orderNo);
                        }
                    }
                    tracker.markStockNotEnough();
                    tracker.record("✗ 库存不足被拦下  orderNo=" + orderNo);
                    resultStore.put(MqOrderResult.failed(orderNo, mode,
                            currentStock(message.getProductId()), orderIdOf(message)));
                    log.warn("【MQ】V{} 库存不足，拒绝落库（业务失败，正常 ack）| {}",
                            mode, message.describe());
                    break;
            }

        } catch (Exception e) {
            /*
             * 处理异常：
             *  1) 释放幂等键 —— 否则 Spring 重试第 2 次进来会被自己的坑挡住，
             *     消息看起来"处理过了"，实际一条订单都没落，这是更隐蔽的丢单。
             *  2) 必须把异常抛出去，交给 Spring 的重试 + 死信机制，
             *     绝不能让消息静默消失。
             */
            if (idemAcquired) {
                idempotentGuard.release(messageId);
            }
            tracker.markError(messageId);
            tracker.record("✗ 处理异常，将重试  orderNo=" + orderNo + "  " + e.getClass().getSimpleName());
            log.error("【MQ】落库处理失败（将触发重试，重试耗尽后进死信队列）| {}",
                    message.describe(), e);
            throw e;

        } finally {
            // 无论成功失败都要 -1（按 messageId 幂等），否则压测的"排空等待"会一直卡住
            tracker.markDone(messageId);
        }
    }

    /* ==================== 内部工具 ==================== */

    private Long orderIdOf(OrderCreateMessage message) {
        return message.getOrder() == null ? null : message.getOrder().getId();
    }

    private Integer currentStock(Long productId) {
        // 只读 Redis 库存用于展示；Redis 没预热（V6/V7）时返回 null，前端显示"—"
        Long stock = stockRedisService.getStock(productId);
        return stock == null ? null : stock.intValue();
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
