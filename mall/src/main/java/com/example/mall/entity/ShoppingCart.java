package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购物车（对应表 shopping_cart）。
 *
 * <p><b>课上重点：为什么购物车表没有 deleted 逻辑删除字段？</b><br>
 * 它是"过程数据"，用户删掉就是真删。如果做成逻辑删除，
 * 用户删了再加同一商品会撞唯一索引 {@code uk_user_product}，
 * 你还得额外写"恢复已删除记录"的逻辑，得不偿失。
 * <b>区分"业务数据"和"过程数据"是设计表时的重要思路。</b></p>
 *
 * <p>另外注意：购物车只存 {@code product_id} 和 {@code quantity}，
 * <b>绝不存商品价格</b>。价格要在下单那一刻从 product 表读，
 * 否则运营改了价，购物车还是老价格，会出大问题。</p>
 */
@Data
@TableName("shopping_cart")
public class ShoppingCart implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    /** 是否勾选结算：1 是，0 否 */
    private Integer selected;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
