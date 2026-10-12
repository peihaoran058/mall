/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderCoreService
 *  com.example.mall.order.OrderPersistSupport
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.example.mall.order;

import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.order.OrderPersistSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCoreService {
    private static final Logger log = LoggerFactory.getLogger(OrderCoreService.class);
    private final OrderPersistSupport support;

    @Transactional(rollbackFor={Exception.class})
    public Orders createOrder(Long userId, OrderSubmitDTO dto) {
        return this.support.doCreateOrder(userId, dto);
    }

    public OrderCoreService(OrderPersistSupport support) {
        this.support = support;
    }
}

