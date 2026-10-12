package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class CartQuantityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "购物车条目 id 不能为空")
    private Long cartId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 999, message = "单个商品最多 999 件")
    private Integer quantity;
}
