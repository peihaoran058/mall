package com.example.mall.mq.v7;

import com.example.mall.entity.Orders;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mq.OrderCreateMessage;
import com.example.mall.mq.OrderPersistResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * 【V7/V8 · 正确版】数据库兜底 + 原子扣减落库
 * ============================================================================
 * 【讲解顺序 ⑥】和 MqIdempotentGuard 一起讲；讲完指出短板（洪峰全量进队列）引出 V8。
 *
 * <p><b>V7 与 V8 的消费端共用本类</b>——V8 的差别只在入口的 Redis 预扣
 * （见 {@link com.example.mall.mq.OrderMqService}），不在落库。</p>
 *
 * <p><b>为什么事务方法单独成类，不写在消费者的监听方法上？</b><br>
 * "同类内部方法调用"会绕过 Spring 的事务代理 —— 这正是第 10 课时
 * T4 那个坑（{@code this.xxx()} 导致 @Transactional 静默失效）。
 * 把事务方法独立成 Bean，由消费者<b>跨类调用</b>，事务才会真正生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistV7Service {

    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 幂等 + 数据库兜底 + 落库，三步都在一个事务里。
     *
     * <p><b>① 幂等</b><br>
     * RabbitMQ 的投递语义是"至少一次"，不是"恰好一次"：发送方确认超时、
     * 消费者处理完但 ack 丢失，都会导致同一条消息被重投。
     * 所以第一步先按 {@code orderId}（雪花主键）查一次订单表，
     * 已存在就直接跳过 —— 天然幂等，不需要额外的去重表。
     * （Redis SETNX 那道更快的闸门在 {@link MqIdempotentGuard} 里，
     * 这里是不依赖任何中间件的第二层。）</p>
     *
     * <p><b>② 数据库兜底</b><br>
     * 即使入口那边放行了（V8 的 Redis 预扣），这里仍然用 V5 那条
     * <b>带条件的原子 UPDATE</b>：
     * <pre>
     * UPDATE product SET stock = stock - n, sales = sales + n
     * WHERE id = ? AND stock &gt;= n
     * </pre>
     * rows=0 说明数据库里其实没货了 → 拒绝落库。
     * 这才是整个方案的安全底线：<b>Redis 可以出错（数据丢了、被人工改错、
     * 预热时算错），但只要这条 SQL 在，库存就永远不会变成负数。</b>
     * 口诀：<b>Redis 管放行，数据库管兜底；宁可少卖，绝不超卖。</b></p>
     *
     * <p><b>③ 落库</b><br>
     * 扣减和插单<b>在同一个事务里</b>：插单失败（比如极端情况下的主键冲突）
     * 会把前面的库存扣减一起回滚，不会出现"扣了库存但没有订单"的账不平。</p>
     *
     * <p>⚠️ 注意本方法<b>不</b>在这里回补 Redis：数据库事务有可能回滚，
     * 而 Redis 操作不受事务管辖 —— 一旦在事务内改了 Redis 又回滚，
     * 就会出现"Redis 加了库存、数据库却当没事发生"的双写不一致。
     * 所以这里只<b>返回</b>结论，具体的补偿动作交给调用方在事务<b>之外</b>做。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPersistResult persistAtomically(OrderCreateMessage message) {
        Orders order = requireOrder(message);

        /* ---------- ① 幂等：同一条消息重投时直接跳过 ---------- */
        if (ordersMapper.selectById(order.getId()) != null) {
            log.warn("【MQ】重复消息，幂等跳过 | orderNo={} | {}", order.getOrderNo(), message.describe());
            return OrderPersistResult.DUPLICATE;
        }

        /* ---------- ② 数据库兜底：带条件的原子扣减 ---------- */
        int rows = productMapper.deductStock(message.getProductId(), message.getQuantity());
        if (rows == 0) {
            log.warn("【MQ】数据库兜底拦下（DB 库存不足）| productId={} | {}", message.getProductId(), message.describe());
            return OrderPersistResult.STOCK_NOT_ENOUGH;
        }

        /* ---------- ③ 落库：订单 + 明细，与上面的扣减同事务 ---------- */
        ordersMapper.insert(order);
        orderItemMapper.insert(message.getItem());
        return OrderPersistResult.PERSISTED;
    }

    /* ==================== 内部工具 ==================== */

    private Orders requireOrder(OrderCreateMessage message) {
        Orders order = message.getOrder();
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("消息里的订单为空：" + message.describe());
        }
        return order;
    }
}
