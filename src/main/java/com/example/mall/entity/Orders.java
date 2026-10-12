package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单主表（对应表 orders）。
 *
 * <p>类名用 {@code Orders} 而不是 {@code Order}，因为 {@code order} 是 SQL 保留字，
 * 写成 {@code @TableName("order")} 大多数数据库会直接报语法错误。</p>
 *
 * <p><b>课上重点：为什么主表和明细要拆开？</b><br>
 * 一个订单有多个商品，如果塞成一行（{@code product_ids} 逗号拼接）会：</p>
 * <ul>
 *     <li>查"某商品的所有订单"要 like 扫描，索引失效</li>
 *     <li>没法记录每个商品的成交价、数量</li>
 *     <li>违反第一范式，后续统计、退换货都没法做</li>
 * </ul>
 *
 * <p><b>状态字段为什么要拆成 pay_status 和 order_status 两个？</b><br>
 * 一个状态字段硬编码 0~9 表示所有情况，看起来简单，实际很快就乱了：
 * "已支付但未发货"和"未支付但取消了"用同一个字段表达会互相覆盖。
 * 支付状态和履约状态是两个独立维度，拆开才讲得清。</p>
 */
@Data
@TableName("orders")
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 订单号，对用户暴露的业务主键 */
    private String orderNo;

    private Long userId;

    /** 订单总金额（未优惠前的合计） */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 0 未支付 1 已支付 2 已退款 */
    private Integer payStatus;

    /** 0 待付款 1 待发货 2 已发货 3 已完成 4 已取消 */
    private Integer orderStatus;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ============ 非表字段 ============ */

    /** 订单明细，查详情时填充 */
    @TableField(exist = false)
    private List<OrderItem> items;
}
