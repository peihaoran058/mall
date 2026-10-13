package com.example.mall.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mall.common.ResultCode;
import com.example.mall.config.MallProperties;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mq.v8.StockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================================
 * 秒杀异步下单（第20课时）—— 生产者侧：投递 + 结果回查
 * ============================================================================
 * 【讲解顺序 ④ · 入口】<b>V8 对比点一</b>：V6/V7 直接投递，
 * V8 在这里多一步 Redis Lua 预扣（看 preDeductEnabled() 那一段）。
 *
 * <p><b>完整链路（V8 最终版）：</b></p>
 * <pre>
 *  用户请求
 *     │
 *     ├─ ① Redis Lua 原子预扣 ──── 库存不足 → 立刻返回 3003（绝大多数请求死在这里）
 *     │      （内存操作，单线程原子，几十万 QPS；不占 DB 连接、不发消息）
 *     │
 *     ├─ ② 查商品 → 构造订单快照 ── 只有"抢到"的少量请求才会走到这，DB 压力被库存量封顶
 *     │
 *     ├─ ③ 发 MQ 消息 ──────────── 削峰：洪峰被队列"存"下来
 *     │
 *     └─ ④ 立刻返回"抢到了" ──────── 用户响应时间只剩一次 Redis 往返
 *
 *  另一边（异步、不影响用户）：
 *  MQ 消费者 ──> 幂等闸门 ──> DB 原子扣减（兜底，rows=0 就拒绝）
 *                            ├─ 成功 → 插订单
 *                            └─ 失败 → 回补 Redis 库存 + 告警
 * </pre>
 *
 * <p><b>三层防护各自负责什么（这是本课时最该记的一张表）：</b></p>
 * <table border="1">
 *     <caption>每一层的职责</caption>
 *     <tr><th>层</th><th>作用</th><th>失效后果</th></tr>
 *     <tr><td>① Redis Lua</td><td>扛住全部并发，挡住无效请求</td>
 *         <td>性能退化（回到 V5/V7 的水平），但<b>不会超卖</b></td></tr>
 *     <tr><td>② MQ</td><td>削峰填谷，把并发写变成匀速消费</td>
 *         <td>用户等待变长；<b>不影响正确性</b></td></tr>
 *     <tr><td>③ DB 条件 UPDATE</td><td><b>最终兜底，保证不超卖</b></td>
 *         <td>会超卖 —— 所以这一层绝对不能省</td></tr>
 * </table>
 *
 * <p><b>⚠️ 必须诚实讲清的代价：</b></p>
 * <ul>
 *     <li><b>最终一致，不是强一致</b>：用户看到"抢到了"，但订单可能几秒后才出现在
 *         "我的订单"里。前端必须做结果回查（{@link #result}）+ 兜底提示（"排队中"）。</li>
 *     <li><b>复杂度大幅上升</b>：多了一套 MQ 要运维、多了幂等和补偿逻辑要写、
 *         多了 Redis 与 DB 的对账任务要跑。V5 一条 SQL 就解决的事，
 *         这里用了三个组件 —— <b>所以不要滥用，只在真的扛不住时才上这套。</b></li>
 *     <li><b>Redis 数据丢了怎么办</b>：库存变成"未预热"（-2），此时必须拒绝下单
 *         并告警，而不是当成"卖完了"。恢复靠预热接口（{@code POST /api/demo/stock/warmup}）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMqService {

    private final MqModeService mqModeService;
    private final StockRedisService stockRedisService;
    private final MqOrderFactory orderFactory;
    private final MqResultStore resultStore;
    private final OrderMessageTracker tracker;
    private final RabbitTemplate rabbitTemplate;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final MallProperties mallProperties;

    /* ==================================================================
     * 一、投递：秒杀入口
     * ================================================================== */

    /** 秒杀下单（默认买 1 件） */
    public Map<String, Object> submit(Long userId, Long productId, Integer quantity) {
        return submit(userId, productId, quantity, null, null, null, null);
    }

    /**
     * 秒杀下单入口。
     *
     * <p><b>返回的只有 orderNo + status=QUEUING，没有订单实体</b>，
     * 因为此刻订单还没落库 —— 真正下单的是消费者。
     * 调用方必须拿着 orderNo 去 {@link #result} 轮询最终结果。</p>
     *
     * @param remark 订单备注；洪峰实验会传 {@code loadtest}，
     *               这样实验产生的订单能被 {@code POST /api/demo/orders/clean} 一键清掉
     */
    public Map<String, Object> submit(Long userId, Long productId, Integer quantity,
                                      String receiverName, String receiverPhone,
                                      String receiverAddress, String remark) {
        long startedAt = System.currentTimeMillis();
        int mode = mqModeService.current();
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;

        /* ---------- ① 【V8 专属】Redis Lua 原子预扣：全链路唯一的"放行"决策点 ---------- */
        if (mqModeService.preDeductEnabled()) {
            long remain = stockRedisService.tryDeduct(productId, qty);

            if (remain == StockRedisService.RESULT_NOT_WARMED) {
                /*
                 * 未预热（-2）是【故障】，不是"卖完了"。
                 * 必须显式报错让人发现，绝不能降级成"库存不足"把问题藏起来 ——
                 * 藏起来的结果是 Redis 数据丢了整整半小时都没人知道，
                 * 直到有人发现"商品明明有货却一直提示抢完"。
                 */
                log.error("【MQ】Redis 库存未预热，下单被拒绝 | productId={}", productId);
                throw new BusinessException(
                        "库存数据未就绪（Redis 未预热）：请先在 MQ 实验台跑一次洪峰实验，"
                                + "或调用 POST /api/demo/stock/warmup?productId=" + productId + " &stock=当前库存");
            }
            if (remain == StockRedisService.RESULT_INSUFFICIENT) {
                // 洪峰实验里绝大多数请求都从这里返回。一条日志都不打：一打就是日志洪峰。
                tracker.markGateRejected();
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
            }
            if (remain == 0) {
                log.info("【MQ】V8 预扣成功，刚好扣完（Redis 库存归零）| productId={}", productId);
            }
        }

        // 走到这里说明抢到了名额。注意：V8 里此刻 Redis 库存已经扣掉，
        // 后面任何一步失败，都必须把这次预扣"还回去"，否则就是少卖。

        /* ---------- ② 只有抢到的请求才查库、构造订单快照 ---------- */
        Product product = orderFactory.requireProduct(productId);
        Orders order = orderFactory.buildOrder(userId, product, qty,
                receiverName, receiverPhone, receiverAddress, remark);
        OrderItem item = orderFactory.buildItem(order, product, qty);

        /* ---------- ③ 发 MQ：把落库这件事交给消费者 ---------- */
        OrderCreateMessage message = new OrderCreateMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setUserId(userId);
        message.setProductId(productId);
        message.setQuantity(qty);
        message.setMode(mode);
        message.setSentAt(LocalDateTime.now());
        message.setOrder(order);
        message.setItem(item);

        // 先记"在途"，再发消息 —— 顺序很重要：如果反过来，消息可能已经被消费完了
        // 才计数，压测的排空等待就会误判成"还没开始"。
        tracker.markSent(message.getMessageId());
        try {
            rabbitTemplate.convertAndSend(
                    mallProperties.getMq().getOrderExchange(),
                    mallProperties.getMq().getOrderRoutingKey(),
                    message);
        } catch (Exception e) {
            /*
             * ★ 发送失败必须回补 Redis 库存！
             * 这是"Redis 预扣 + MQ 异步"架构里最容易被漏掉的一环：
             * Redis 那边已经扣了，消息却没发出去，这笔库存就凭空消失了。
             * 生产环境的完整解法是"本地消息表 + 定时补偿"或事务消息，
             * 这里用最直接的方式：发送失败立刻回补，并让用户看到失败、可以重试。
             */
            tracker.markError(message.getMessageId());
            tracker.markDone(message.getMessageId());
            if (mqModeService.preDeductEnabled()) {
                stockRedisService.restore(productId, qty);
            }
            log.error("【MQ】下单消息发送失败，已回补 Redis 库存 | productId={} | {}",
                    productId, message.describe(), e);
            throw new BusinessException("系统繁忙，下单失败请重试");
        }

        /* ---------- ④ 立刻返回：用户不需要等落库 ---------- */
        resultStore.put(MqOrderResult.queuing(order.getOrderNo(), mode,
                redisStock(productId), order.getId()));

        Integer remainStock = redisStock(productId);
        log.info("【MQ】V{} 投递成功 | orderNo={} | Redis 剩余={} | 耗时 {}ms",
                mode, order.getOrderNo(), remainStock, System.currentTimeMillis() - startedAt);

        Map<String, Object> data = new LinkedHashMap<>(12);
        data.put("orderNo", order.getOrderNo());
        data.put("orderId", order.getId());
        data.put("status", MqOrderResult.STATUS_QUEUING);
        data.put("message", "已进入队列，正在排队下单…");
        data.put("mode", mode);
        data.put("quantity", qty);
        data.put("stock", remainStock);
        data.put("exchange", mallProperties.getMq().getOrderExchange());
        data.put("queue", mallProperties.getMq().getOrderQueue());
        data.put("routingKey", mallProperties.getMq().getOrderRoutingKey());
        data.put("messageId", message.getMessageId());
        data.put("submitMs", System.currentTimeMillis() - startedAt);
        return data;
    }

    /* ==================================================================
     * 二、结果回查：异步下单的"最后一公里"
     * ================================================================== */

    /**
     * 查秒杀排队的最终结果。
     *
     * <p>三个数据来源，按可信度从高到低：</p>
     * <ol>
     *     <li><b>内存结果缓存</b>：消费者处理完就写进来，FAILED 只能从这里拿到；</li>
     *     <li><b>订单表</b>：最权威。服务重启丢了缓存、或者缓存过期了，
     *         只要订单在库里，就一定是 SUCCESS；</li>
     *     <li><b>都没有</b> → 说明还在队列里排队，回 QUEUING 让前端继续轮询。</li>
     * </ol>
     */
    public Map<String, Object> result(String orderNo, Long userId) {
        MqOrderResult cached = resultStore.get(orderNo);
        int mode = mqModeService.current();

        // ① 已经有终态（SUCCESS / FAILED）直接用，不用查库
        if (cached != null && !MqOrderResult.STATUS_QUEUING.equals(cached.getStatus())) {
            return cached.toMap();
        }

        // ② 兜底查订单表：订单在库里 = 一定成功（哪怕结果缓存被清了）
        Orders order = ordersMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo));
        if (order != null) {
            if (userId != null && order.getUserId() != null && !order.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.ORDER_NO_PERMISSION);
            }
            MqOrderResult success = MqOrderResult.success(orderNo, mode,
                    redisStock(productIdOf(orderNo)), order.getId());
            resultStore.put(success);
            return success.toMap();
        }

        // ③ 查不到：要么排队中，要么这条消息压根没成功过
        if (cached != null) {
            return cached.toMap();
        }
        return MqOrderResult.queuing(orderNo, mode, null, null).toMap();
    }

    /* ==================== 内部工具 ==================== */

    /** 读 Redis 库存（只用于展示，未预热时返回 null，前端显示 "—"） */
    private Integer redisStock(Long productId) {
        if (productId == null) {
            return null;
        }
        Long stock = stockRedisService.getStock(productId);
        return stock == null ? null : stock.intValue();
    }

    /** 按订单号反查商品 id（订单明细里存了商品快照，顺手拿一下用于展示库存） */
    private Long productIdOf(String orderNo) {
        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo)
                .last("limit 1"));
        return item == null ? null : item.getProductId();
    }
}
