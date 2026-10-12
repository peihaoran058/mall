/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderPersistSupport
 *  com.example.mall.order.OrderVersionV2Service
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
public class OrderVersionV2Service {
    private static final Logger log = LoggerFactory.getLogger(OrderVersionV2Service.class);
    private final OrderPersistSupport support;

    @Transactional(rollbackFor={Exception.class})
    public synchronized Orders submit(Long userId, OrderSubmitDTO dto) {
        log.info("\u3010V2 \u9519\u8bef\u793a\u8303\u3011\u4e8b\u52a1 + synchronized \u540c\u65b9\u6cd5 | \u7ebf\u7a0b={}", (Object)Thread.currentThread().getName());
        return this.support.doCreateOrder(userId, dto);
    }

    public OrderVersionV2Service(OrderPersistSupport support) {
        this.support = support;
    }
}

