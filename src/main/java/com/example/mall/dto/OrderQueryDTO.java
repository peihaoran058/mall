package com.example.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单状态筛选，null 表示全部 */
    private Integer orderStatus;

    /** 支付状态筛选 */
    private Integer payStatus;

    /** 订单号，精确查询（后台用） */
    private String orderNo;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
