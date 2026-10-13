package com.example.mall.order.oversell;

import com.example.mall.common.Constants;
import com.example.mall.config.MallProperties;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.util.OrderNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ============================================================================
 * 超卖实验的公共支撑：查商品、插订单、放大竞态窗口
 * ============================================================================
 *
 * <p>V1~V5 五个版本共用这些步骤，保证它们之间<b>唯一的变量就是"怎么扣库存"</b> ——
 * 只有这样，压测结果的差异才能归因到锁方案上，课堂对比才成立。</p>
 *
 * <p>和真实下单链路（购物车结算）的差别：超卖实验直接"买 1 件指定商品"，
 * 不走购物车。因为压测要同时发 20 个请求，如果都挤购物车，
 * 第一个请求就把购物车清空了，后面 19 个只会报"请先勾选要结算的商品"，
 * 根本压不到库存这一步。独立账号 + 独立购物车是 Node 网关老压测的做法，
 * 这里直接绕开购物车，实验更纯粹。</p>
 */
@Component
@RequiredArgsConstructor
public class OversellSupport {

    /** 压测订单的标记：清理订单时就认这个 remark，不会误删真实订单 */
    public static final String LOADTEST_REMARK = "loadtest";

    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final MallProperties mallProperties;

    /** 查商品，查不到直接报 2001，压测入口会把这个错误透传给前端 */
    public Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在：id=" + productId);
        }
        return product;
    }

    /**
     * 【教学开关】在"读库存"和"扣库存"之间睡 raceWindowMillis 毫秒。
     *
     * <p>真实项目里这个窗口来自网络往返、GC、SQL 执行时间，天然存在；
     * 但本地 MySQL 太快，20 个线程可能根本撞不上这个窗口，
     * V1 就压不出超卖。把它调成 5ms，课堂上一次就能稳定复现。</p>
     */
    public void raceWindow() {
        Long millis = mallProperties.getOrder().getRaceWindowMillis();
        if (millis == null || millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 下单落库：插 orders + order_item。
     *
     * <p>固定买 1 件，收货人信息写死成压测数据，订单上带
     * {@link #LOADTEST_REMARK} 标记，压测完靠它一键清理。</p>
     */
    public Orders createOrder(Long userId, Product product) {
        long orderId = OrderNoUtil.snowflakeLike();
        String orderNo = OrderNoUtil.timeRandom();
        BigDecimal price = product.getPrice();

        Orders orders = new Orders();
        orders.setId(orderId);
        orders.setOrderNo(orderNo);
        orders.setUserId(userId);
        orders.setTotalAmount(price);
        orders.setPayAmount(price);
        orders.setPayStatus(Constants.PAY_STATUS_UNPAID);
        orders.setOrderStatus(Constants.ORDER_STATUS_UNPAID);
        orders.setReceiverName("压测收货人");
        orders.setReceiverPhone("13800000000");
        orders.setReceiverAddress("压测地址（超卖实验）");
        orders.setRemark(LOADTEST_REMARK);
        ordersMapper.insert(orders);

        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setOrderNo(orderNo);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductCover(product.getCover());
        item.setPrice(price);
        item.setQuantity(1);
        item.setTotalAmount(price);
        orderItemMapper.insert(item);

        return orders;
    }
}
