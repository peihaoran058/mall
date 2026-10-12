/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderAsyncService
 *  com.example.mall.order.OrderCoreService
 *  com.example.mall.order.OrderVersionV3Service
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.example.mall.order;

import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.order.OrderAsyncService;
import com.example.mall.order.OrderCoreService;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderVersionV3Service {
    private static final Logger log = LoggerFactory.getLogger(OrderVersionV3Service.class);
    private static final Object ORDER_LOCK = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();
    private final OrderCoreService orderCoreService;
    private final OrderAsyncService orderAsyncService;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        Object object = ORDER_LOCK;
        synchronized (object) {
            Orders order = this.orderCoreService.createOrder(userId, dto);
            this.orderAsyncService.asyncRecordLog(order.getOrderNo(), "V3 \u5355\u673a\u9501\u7248\u672c\u4e0b\u5355\u6210\u529f");
            return order;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Orders submitWithReentrantLock(Long userId, OrderSubmitDTO dto) {
        REENTRANT_LOCK.lock();
        try {
            Orders order = this.orderCoreService.createOrder(userId, dto);
            this.orderAsyncService.asyncRecordLog(order.getOrderNo(), "V3 ReentrantLock \u7248\u672c\u4e0b\u5355\u6210\u529f");
            Orders orders = order;
            return orders;
        }
        finally {
            REENTRANT_LOCK.unlock();
        }
    }

    public OrderVersionV3Service(OrderCoreService orderCoreService, OrderAsyncService orderAsyncService) {
        this.orderCoreService = orderCoreService;
        this.orderAsyncService = orderAsyncService;
    }
}

