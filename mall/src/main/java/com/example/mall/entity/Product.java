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

/**
 * 商品（对应表 product）。
 *
 * <p><b>课上重点一：金额为什么必须用 {@link BigDecimal}？</b><br>
 * {@code 0.1 + 0.2 = 0.30000000000000004}（double 二进制精度问题）。
 * 商城里这种误差累积起来就是对不上账。BigDecimal 比较大小要用
 * {@code compareTo()}，因为 {@code equals()} 会比较 scale
 * （{@code 1.0} 和 {@code 1.00} 用 equals 是不相等的）。</p>
 *
 * <p><b>课上重点二：stock 是并发超卖实验的核心字段</b><br>
 * {@code UPDATE product SET stock = stock - 1 WHERE id = ? AND stock >= 1}
 * —— 把判断和扣减放在同一条 SQL 里，这是不用锁也能防超卖的"乐观减库存"写法，
 * 第13课时会拿它和 {@code stock = stock - 1}（无判断）对比。</p>
 */
@Data
@TableName("product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    private String cover;

    /** 轮播图，逗号分隔 */
    private String images;

    private String detail;

    private BigDecimal price;

    /** 原价（划线价） */
    private BigDecimal originalPrice;

    /** 库存 —— 并发超卖实验核心字段 */
    private Integer stock;

    private Integer sales;

    /** 1 上架 0 下架 */
    private Integer status;

    private Integer sort;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ============ 非表字段 ============ */

    /** 分类名称，联表查询时填充，方便后台列表展示 */
    @TableField(exist = false)
    private String categoryName;
}
