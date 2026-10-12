/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderAsyncService
 *  com.example.mall.order.OrderCoreService
 *  com.example.mall.order.OrderVersionV1Service
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.example.mall.order;

import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.order.OrderAsyncService;
import com.example.mall.order.OrderCoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderVersionV1Service {
    private static final Logger log = LoggerFactory.getLogger(OrderVersionV1Service.class);
    private final OrderCoreService orderCoreService;
    private final OrderAsyncService orderAsyncService;

    public Orders submit(Long userId, OrderSubmitDTO dto) {
        Orders order = this.orderCoreService.createOrder(userId, dto);
        this.orderAsyncService.asyncRecordLog(order.getOrderNo(), "V1 \u65e0\u9501\u7248\u672c\u4e0b\u5355\u6210\u529f");
        return order;
    }

    public OrderVersionV1Service(OrderCoreService orderCoreService, OrderAsyncService orderAsyncService) {
        this.orderCoreService = orderCoreService;
        this.orderAsyncService = orderAsyncService;
    }
}

