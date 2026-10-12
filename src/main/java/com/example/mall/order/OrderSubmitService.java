/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.config.MallProperties
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.order.OrderSubmitService
 *  com.example.mall.order.OrderVersionV1Service
 *  com.example.mall.order.OrderVersionV2Service
 *  com.example.mall.order.OrderVersionV3Service
 *  com.example.mall.order.OrderVersionV4Service
 *  com.example.mall.order.OrderVersionV5Service
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.example.mall.order;

import com.example.mall.config.MallProperties;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.order.OrderVersionV1Service;
import com.example.mall.order.OrderVersionV2Service;
import com.example.mall.order.OrderVersionV3Service;
import com.example.mall.order.OrderVersionV4Service;
import com.example.mall.order.OrderVersionV5Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderSubmitService {
    private static final Logger log = LoggerFactory.getLogger(OrderSubmitService.class);
    private final MallProperties mallProperties;
    private final OrderVersionV1Service v1Service;
    private final OrderVersionV2Service v2Service;
    private final OrderVersionV3Service v3Service;
    private final OrderVersionV4Service v4Service;
    private final OrderVersionV5Service v5Service;

    public Orders submit(Long userId, OrderSubmitDTO dto) {
        Integer version = this.mallProperties.getOrder().getVersion();
        log.info(">>> \u5f53\u524d\u4e0b\u5355\u7248\u672c V{} | userId={}", (Object)version, (Object)userId);
        if (version == null) {
            version = 1;
        }
        switch (version) {
            case 2: {
                return this.v2Service.submit(userId, dto);
            }
            case 3: {
                return this.v3Service.submit(userId, dto);
            }
            case 4: {
                return this.v4Service.submit(userId, dto);
            }
            case 5: {
                return this.v5Service.submit(userId, dto);
            }
        }
        return this.v1Service.submit(userId, dto);
    }

    public OrderSubmitService(MallProperties mallProperties, OrderVersionV1Service v1Service, OrderVersionV2Service v2Service, OrderVersionV3Service v3Service, OrderVersionV4Service v4Service, OrderVersionV5Service v5Service) {
        this.mallProperties = mallProperties;
        this.v1Service = v1Service;
        this.v2Service = v2Service;
        this.v3Service = v3Service;
        this.v4Service = v4Service;
        this.v5Service = v5Service;
    }
}

