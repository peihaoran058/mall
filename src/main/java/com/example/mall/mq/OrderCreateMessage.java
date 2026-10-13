package com.example.mall.mq;

import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * 订单落库消息（第20课时）
 * ============================================================================
 * 【讲解顺序 ③ · 公共消息体】和 MqOrderFactory 一起讲：消息里带什么、
 * 为什么 orderId 快照要在入口就生成（它是幂等的根基）。
 *
 * <p>入口（Redis 预扣成功 / 直接投递）之后，投递给 MQ 的消息体。
 * 消费者拿到它只做一件事：<b>把订单和明细写进数据库</b>。</p>
 *
 * <p><b>设计要点 1：消息里带着"算好的订单快照"，而不是只给一个商品 id。</b><br>
 * 如果消息只说"用户 1001 买了商品 1 一件"，消费者就得再去查商品表拿价格、名称、
 * 封面 —— 这等于把本该在入口层做完的事推到了消费端，消费端压力更大，
 * 而且商品在排队期间被改价会导致落库的价格和下单时不一致。
 * 所以订单对象在<b>进入 MQ 之前就构造好</b>，消息自包含。</p>
 *
 * <p><b>设计要点 2：messageId 和 orderId 都要有，但用途完全不同。</b></p>
 * <ul>
 *     <li>{@code messageId}：这条消息的唯一标识，用于日志追踪（一次下单从入口
 *         到落库的完整轨迹）和<b>幂等去重</b>（V7/V8 用它抢 Redis SETNX 坑）；</li>
 *     <li>{@code orderId}：订单主键（雪花算法生成），<b>数据库层的幂等依据</b>。
 *         消费者落库前会按它查一次订单表，已存在就直接跳过 ——
 *         这条防线不依赖任何中间件，是最后也最硬的一道。</li>
 * </ul>
 *
 * <p><b>设计要点 3：字段类型必须能被 JSON 序列化。</b><br>
 * 消息用 Jackson 转 JSON（而不是 JDK 序列化），这样在 RabbitMQ 管理界面里
 * 能直接看到消息内容，排查问题时不用反序列化二进制。代价是必须给
 * {@code ObjectMapper} 注册 {@code JavaTimeModule}，否则 {@link LocalDateTime}
 * 会直接抛 {@code InvalidDefinitionException}（见 {@link RabbitMqConfig}）。</p>
 */
@Data
public class OrderCreateMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一标识（UUID），用于日志串联 + Redis 幂等键 */
    private String messageId;

    /** 下单用户 */
    private Long userId;

    /** 商品 id（消费端做 DB 兜底扣减时要用） */
    private Long productId;

    /** 购买数量（DB 兜底扣减时要用，必须和订单明细里的数量一致） */
    private Integer quantity;

    /** 投递时生效的消费端版本（只做留痕，排查用：这条消息是被哪一版处理的） */
    private Integer mode;

    /** 消息发送时间 */
    private LocalDateTime sentAt;

    /** 已经构造好的订单（未落库，主键已生成） */
    private Orders order;

    /** 已经构造好的订单明细（未落库，含商品名称/价格快照） */
    private OrderItem item;

    /** 日志用的简短描述 */
    public String describe() {
        return "messageId=" + messageId
                + " | orderNo=" + (order == null ? null : order.getOrderNo())
                + " | userId=" + userId
                + " | productId=" + productId
                + " x" + quantity;
    }
}
