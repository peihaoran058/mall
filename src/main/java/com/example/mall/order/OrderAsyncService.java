/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.entity.OrderLog
 *  com.example.mall.mapper.OrderLogMapper
 *  com.example.mall.order.OrderAsyncService
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.scheduling.annotation.Async
 *  org.springframework.stereotype.Service
 */
package com.example.mall.order;

import com.example.mall.entity.OrderLog;
import com.example.mall.mapper.OrderLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderAsyncService {
    private static final Logger log = LoggerFactory.getLogger(OrderAsyncService.class);
    private final OrderLogMapper orderLogMapper;

    @Async(value="mallExecutor")
    public void asyncRecordLog(String orderNo, String content) {
        try {
            log.info("\u3010\u5f02\u6b65\u7ebf\u7a0b\u3011\u5f00\u59cb\u5199\u8ba2\u5355\u65e5\u5fd7 | orderNo={} | thread={}", (Object)orderNo, (Object)Thread.currentThread().getName());
            OrderLog orderLog = new OrderLog();
            orderLog.setOrderNo(orderNo);
            orderLog.setContent(content);
            orderLog.setThreadName(Thread.currentThread().getName());
            this.orderLogMapper.insert(orderLog);
            log.info("\u3010\u5f02\u6b65\u7ebf\u7a0b\u3011\u8ba2\u5355\u65e5\u5fd7\u5199\u5165\u5b8c\u6210 | orderNo={}", (Object)orderNo);
        }
        catch (Exception e) {
            log.error("\u3010\u5f02\u6b65\u7ebf\u7a0b\u3011\u5199\u8ba2\u5355\u65e5\u5fd7\u5931\u8d25 | orderNo={}", (Object)orderNo, (Object)e);
        }
    }

    public void selfInvocationDemo(String orderNo) {
        log.info("\u3010\u9519\u8bef\u793a\u8303\u3011\u5373\u5c06\u901a\u8fc7 this \u8c03\u7528 @Async \u65b9\u6cd5 | thread={}", (Object)Thread.currentThread().getName());
        this.asyncRecordLog(orderNo, "\u540c\u7c7b\u5185\u90e8\u8c03\u7528 @Async \u2014\u2014 \u672c\u5e94\u5f02\u6b65\uff0c\u5b9e\u9645\u540c\u6b65");
        log.info("\u3010\u9519\u8bef\u793a\u8303\u3011this \u8c03\u7528\u8fd4\u56de | thread={}\uff08\u7ebf\u7a0b\u540d\u6ca1\u53d8\u8bf4\u660e\u5f02\u6b65\u5931\u6548\uff09", (Object)Thread.currentThread().getName());
    }

    public OrderAsyncService(OrderLogMapper orderLogMapper) {
        this.orderLogMapper = orderLogMapper;
    }
}

