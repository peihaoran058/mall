package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull(message = "请选择商品分类")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称最长 200 个字符")
    private String name;

    private String subtitle;

    private String cover;

    private String images;

    private String detail;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    private BigDecimal originalPrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    /** 1 上架 0 下架 */
    private Integer status;

    private Integer sort;
}
