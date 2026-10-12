/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.example.mall.common.ResultCode
 *  com.example.mall.demo.DemoService
 *  com.example.mall.entity.OrderItem
 *  com.example.mall.entity.Orders
 *  com.example.mall.entity.Product
 *  com.example.mall.exception.BusinessException
 *  com.example.mall.mapper.OrderItemMapper
 *  com.example.mall.mapper.OrdersMapper
 *  com.example.mall.mapper.ProductMapper
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.example.mall.demo;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.common.ResultCode;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoService {
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;

    @Transactional(rollbackFor={Exception.class})
    public Map<String, Object> resetStock(Long productId, Integer stock) {
        Product product = (Product)this.productMapper.selectById((Serializable)productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }
        Product update = new Product();
        update.setId(productId);
        update.setStock(stock);
        update.setSales(Integer.valueOf(0));
        this.productMapper.updateById(update);
        HashMap<String, Object> data = new HashMap<String, Object>(4);
        data.put("productId", productId);
        data.put("stock", stock);
        data.put("message", "\u5e93\u5b58\u5df2\u91cd\u7f6e\uff0c\u53ef\u4ee5\u5f00\u59cb\u538b\u6d4b\u4e86");
        log.info("\u3010\u8bfe\u5802\u5b9e\u9a8c\u3011\u91cd\u7f6e\u5e93\u5b58 | productId={} | stock={}", (Object)productId, (Object)stock);
        return data;
    }

    public Map<String, Object> stockInfo(Long productId) {
        Product product = (Product)this.productMapper.selectById((Serializable)productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }
        HashMap<String, Object> data = new HashMap<String, Object>(8);
        data.put("productId", productId);
        data.put("productName", product.getName());
        data.put("stock", product.getStock());
        data.put("sales", product.getSales());
        data.put("oversell", product.getStock() != null && product.getStock() < 0);
        data.put("message", product.getStock() != null && product.getStock() < 0 ? "\u26a0\ufe0f \u5e93\u5b58\u4e3a\u8d1f\u6570\uff0c\u8d85\u5356 BUG \u5df2\u590d\u73b0\uff01" : "\u5e93\u5b58\u6b63\u5e38");
        return data;
    }

    @Transactional(rollbackFor={Exception.class})
    public Map<String, Object> cleanOrders() {
        int itemRows = this.orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery().likeRight(OrderItem::getOrderNo, "MALL"));
        int orderRows = this.ordersMapper.delete(Wrappers.<Orders>lambdaQuery().likeRight(Orders::getOrderNo, "MALL"));
        HashMap<String, Object> data = new HashMap<String, Object>(4);
        data.put("orderRows", orderRows);
        data.put("itemRows", itemRows);
        data.put("message", "\u8ba2\u5355\u6570\u636e\u5df2\u6e05\u7406");
        return data;
    }

    public DemoService(ProductMapper productMapper, OrdersMapper ordersMapper, OrderItemMapper orderItemMapper) {
        this.productMapper = productMapper;
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
    }
}

