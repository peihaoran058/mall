package com.example.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维度名称，如 "已发货" / "2026-09-20" / 商品名 */
    private String name;

    /** 指标值 */
    private Long value;
}
