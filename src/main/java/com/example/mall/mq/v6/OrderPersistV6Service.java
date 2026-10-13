package com.example.mall.mq.v6;

import com.example.mall.config.MallProperties;
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
 * 【V6 · 错误示范】消费端无保护落库：读 → 判断 → 无条件扣减
 * ============================================================================
 * 【讲解顺序 ⑤】第 20 课时的第一个落库版本，和第 13 课时 V1 是同一个 BUG。
 *
 * <p><b>为什么事务方法单独成类，不写在消费者的监听方法上？</b><br>
 * "同类内部方法调用"会绕过 Spring 的事务代理 —— 这正是第 10 课时
 * T4 那个坑（{@code this.xxx()} 导致 @Transactional 静默失效）。
 * 把事务方法独立成 Bean，由消费者<b>跨类调用</b>，事务才会真正生效。</p>
 *
 * <p><b>这正是第 13 课时 V1 的 BUG 原封不动搬到消费端：</b></p>
 * <pre>
 *  线程A  读到 10 ─┐
 *  线程B  读到 10 ─┤ 三个人都判定"够"
 *  线程C  读到 10 ─┘
 *  线程A  扣 → 9
 *  线程B  扣 → 8
 *  线程C  扣 → 7        卖出去 3 件，其实只该卖 1 件？
 *                       更糟的是最后库存会变成负数（因为扣减无条件）
 * </pre>
 *
 * <p><b>这个版本存在的唯一意义，就是证明"MQ 削峰 ≠ 解决超卖"</b>：
 * 请求确实被优雅地排进了队列、用户也确实秒回，但库存还是被抢超了。
 * MQ 把"用户等待"换成了"后台排队"，它从来没有把并发消费变成串行 ——
 * 消费端该有的并发控制，一样都不能少。</p>
 *
 * <p><b>⚠️ 必须讲清的误区：给本方法加上 {@code @Transactional} 并不能救它。</b><br>
 * 事务保证的是"要么全成功要么全回滚"（原子性、一致性），
 * 而超卖是<b>并发下的竞态条件</b> —— 两条事务各自都是合法的：
 * 都读到库存 10、都判断"够"、都扣 1。数据库行锁会让两个 UPDATE 排队，
 * 但排队改变不了"两个都已经判定够"这个事实。所以最后库存照样变成负数。
 * <b>事务和并发控制是两件事。</b></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistV6Service {

    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final MallProperties mallProperties;

    /**
     * 三步分离的扣库存：读 → 判断 → 无条件扣减。
     *
     * <p>{@code raceWindowMillis} 是教学开关：本地 MySQL 太快，
     * 不加窗口三条线程可能根本撞不上，BUG 就演示不出来（见 {@link MallProperties.Mq}）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPersistResult persistWithoutProtection(OrderCreateMessage message) {
        Orders order = requireOrder(message);

        /* ---------- ① 读库存（普通查询，不加锁、不做原子判断） ---------- */
        Integer stock = productMapper.selectStock(message.getProductId());

        /* ---------- ② 放大竞态窗口（教学开关，默认为 0 = 关闭） ---------- */
        raceWindow();

        /* ---------- ③ Java 层判断：这里就是超卖的源头 ---------- */
        if (stock == null || stock < message.getQuantity()) {
            log.info("【MQ】V6 判断库存不足，拒绝落库 | productId={} | 读到库存={} | {}",
                    message.getProductId(), stock, message.describe());
            return OrderPersistResult.STOCK_NOT_ENOUGH;
        }

        /* ---------- ④ 无条件扣减：没有 WHERE stock >= n，一定会扣成负数 ---------- */
        int rows = productMapper.deductStockWithoutCheck(message.getProductId(), message.getQuantity());
        log.info("【MQ】V6 无条件扣减 | productId={} | -{} | affected={} | {}",
                message.getProductId(), message.getQuantity(), rows, message.describe());

        /* ---------- ⑤ 落库 ---------- */
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

    /**
     * 【教学开关】在"读库存"和"扣库存"之间睡 {@code mall.mq.race-window-millis} 毫秒。
     *
     * <p>真实项目里这个窗口来自网络往返、GC、SQL 执行时间，天然存在；
     * 但本地 MySQL 太快，三条消费线程可能根本撞不上，V6 就压不出超卖。
     * 课堂上把它调成 30~50ms，一次就能稳定复现。
     * 演示完记得改回小值 —— 它会让吞吐量变得很难看，那不是这一节要讲的东西。</p>
     */
    private void raceWindow() {
        Long millis = mallProperties.getMq().getRaceWindowMillis();
        if (millis == null || millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
