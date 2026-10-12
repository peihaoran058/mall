/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.config.MallProperties
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderAsyncService
 *  com.example.mall.order.OrderCoreService
 *  com.example.mall.order.OrderVersionV4Service
 *  com.example.mall.util.RedisLockUtil
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.example.mall.order;

import com.example.mall.config.MallProperties;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.order.OrderAsyncService;
import com.example.mall.order.OrderCoreService;
import com.example.mall.util.RedisLockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderVersionV4Service {
    private static final Logger log = LoggerFactory.getLogger(OrderVersionV4Service.class);
    private final OrderCoreService orderCoreService;
    private final OrderAsyncService orderAsyncService;
    private final RedisLockUtil redisLockUtil;
    private final MallProperties mallProperties;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        Orders orders;
        block3: {
            String lockKey = "mall:lock:order:submit";
            String requestId = null;
            try {
                requestId = this.redisLockUtil.tryLock(lockKey, this.mallProperties.getOrder().getLockTimeout().longValue(), this.mallProperties.getOrder().getLockWaitMillis().longValue());
                Orders order = this.orderCoreService.createOrder(userId, dto);
                this.orderAsyncService.asyncRecordLog(order.getOrderNo(), "V4 Redis \u5206\u5e03\u5f0f\u9501\u7248\u672c\u4e0b\u5355\u6210\u529f");
                orders = order;
                if (requestId == null) break block3;
            }
            catch (Throwable throwable) {
                if (requestId != null) {
                    this.redisLockUtil.unlock(lockKey, requestId);
                }
                throw throwable;
            }
            this.redisLockUtil.unlock(lockKey, requestId);
        }
        return orders;
    }

    public OrderVersionV4Service(OrderCoreService orderCoreService, OrderAsyncService orderAsyncService, RedisLockUtil redisLockUtil, MallProperties mallProperties) {
        this.orderCoreService = orderCoreService;
        this.orderAsyncService = orderAsyncService;
        this.redisLockUtil = redisLockUtil;
        this.mallProperties = mallProperties;
    }
}

