package com.example.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车列表项。
 *
 * <p><b>课上重点：为什么要有 VO 层？</b><br>
 * 购物车需要展示商品名称、图片、价格，但这些字段不在 shopping_cart 表里。
 * 直接把 {@code Map<String,Object>} 返回给前端，接口结构就没有契约了 ——
 * 前端不知道有哪些字段、什么类型，改一次问一次。</p>
 *
 * <p>VO（View Object）专门负责"接口长什么样"，和数据库表结构解绑。</p>
 */
@Data
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long cartId;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Integer selected;

    /* ---------- 以下来自 product 表 ---------- */

    private String productName;

    private String productCover;

    private BigDecimal price;

    /** 当前库存，前端用来提示"库存不足" / 置灰加减号 */
    private Integer stock;

    /** 商品状态：1 上架 0 下架。下架商品前端要标"已失效" */
    private Integer productStatus;

    /** 小计金额 = price * quantity，服务端算好，避免前端浮点误差 */
    private BigDecimal totalAmount;

    public BigDecimal getTotalAmount() {
        if (price == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
