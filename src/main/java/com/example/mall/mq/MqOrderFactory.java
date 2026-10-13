package com.example.mall.mq;

import com.example.mall.common.Constants;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.util.OrderNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ============================================================================
 * 订单快照工厂（第20课时）
 * ============================================================================
 * 【讲解顺序 ③ · 公共工厂】和 OrderCreateMessage 一起讲（见 mq 包导读）。
 *
 * <p>秒杀链路里"构造订单"这件事发生在<b>投递 MQ 之前</b>，
 * 而且被两个调用方共用：</p>
 * <ul>
 *     <li>{@link OrderMqService} —— 秒杀页面的真实下单；</li>
 *     <li>{@link MqExperimentService} —— 洪峰实验里灌进去的 N 条消息。</li>
 * </ul>
 * <p>抽成工厂是为了保证两条链路构造出来的订单<b>结构完全一致</b>，
 * 这样压测结果才能代表真实下单的表现。如果各写一份，
 * 很容易出现"实验里带了 remark 标记、真实下单忘了带"这种不对称。</p>
 *
 * <p><b>课上要点：订单号与主键为什么都在这儿生成？</b><br>
 * 因为消息必须自包含（见 {@link OrderCreateMessage} 的说明）。
 * 入口生成好 id 和 orderNo 之后，用户立刻就能拿到 orderNo 去轮询结果，
 * 而主键 id 让消费端可以用"查 id 是否已存在"做数据库层幂等。</p>
 */
@Component
@RequiredArgsConstructor
public class MqOrderFactory {

    private final ProductMapper productMapper;

    /**
     * 查商品（快照的来源）。
     * <p>⚠️ 只在<b>入口</b>调用：V8 里入口已经用 Redis 把请求挡过一遍，
     * 只有抢到名额的少量请求才会走到这里查库 —— 这正是"数据库不会被洪峰打挂"的原因。
     * V6/V7 没有入口门禁，所以每条消息都会查一次库。</p>
     */
    public Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在：id=" + productId);
        }
        return product;
    }

    /**
     * 构造订单主表快照（<b>不落库</b>）。
     *
     * <p>金额由服务端按商品表的当前价格计算 —— 前端传什么价格都不参与计算，
     * 否则改一下请求体就能 1 分钱买手机。</p>
     */
    public Orders buildOrder(Long userId, Product product, int quantity,
                             String receiverName, String receiverPhone, String receiverAddress,
                             String remark) {
        BigDecimal total = product.getPrice() == null
                ? BigDecimal.ZERO
                : product.getPrice().multiply(new BigDecimal(quantity));

        Orders order = new Orders();
        // 主键由雪花算法生成（不是自增）：订单号要在"投递 MQ 之前"就确定下来，
        // 用户拿着它去轮询结果；同时它也是消费端"查 id 是否已存在"的幂等依据。
        order.setId(OrderNoUtil.snowflakeLike());
        order.setOrderNo(OrderNoUtil.timeRandom());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setPayStatus(Constants.PAY_STATUS_UNPAID);
        order.setOrderStatus(Constants.ORDER_STATUS_UNPAID);
        order.setReceiverName(defaultIfBlank(receiverName, "秒杀用户"));
        order.setReceiverPhone(defaultIfBlank(receiverPhone, "13800000000"));
        order.setReceiverAddress(defaultIfBlank(receiverAddress, "秒杀演示地址"));
        order.setRemark(remark);
        return order;
    }

    /**
     * 构造订单明细快照（商品名称/封面/单价都要抄一份，见 {@link OrderItem} 的说明）。
     *
     * @param quantity 购买数量，必须和消息里的 quantity 一致 ——
     *                 不一致就会出现"扣了 1 件、卖出去 2 件"的账不平
     */
    public OrderItem buildItem(Orders order, Product product, int quantity) {
        BigDecimal price = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderNo(order.getOrderNo());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductCover(product.getCover());
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setTotalAmount(price.multiply(new BigDecimal(quantity)));
        return item;
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }
}
