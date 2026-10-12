package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细（对应表 order_item）。
 *
 * <p><b>课上重点：为什么要把商品名称、图片、价格"抄一份"存进来？</b><br>
 * 这叫<b>下单快照</b>。因为商品会改名、会换图、会调价。
 * 如果订单详情每次都去 join 商品表，就会出现：</p>
 * <ul>
 *     <li>用户 3 月买的东西，5 月看订单显示的是现在的名字和价格</li>
 *     <li>商品被删了（下架逻辑删除后），历史订单直接查不出商品信息</li>
 *     <li>对账时无法复现当时的成交价，财务对不上</li>
 * </ul>
 * <p>快照是电商订单系统的铁律：<b>订单一旦生成，就不能再受商品变化影响</b>。
 * 收货地址同理，也要快照。</p>
 */
@Data
@TableName("order_item")
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 商品主图快照 */
    private String productCover;

    /** 成交单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 小计金额 = price * quantity */
    private BigDecimal totalAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
