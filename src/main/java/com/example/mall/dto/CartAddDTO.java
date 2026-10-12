package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class CartAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "商品 id 不能为空")
    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 999, message = "单次最多购买 999 件")
    private Integer quantity;
}
