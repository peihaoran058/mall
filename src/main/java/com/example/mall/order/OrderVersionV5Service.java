/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.example.mall.common.Constants
 *  com.example.mall.common.ResultCode
 *  com.example.mall.dto.OrderSubmitDTO
 *  com.example.mall.entity.OrderItem
 *  com.example.mall.entity.Orders
 *  com.example.mall.entity.Product
 *  com.example.mall.entity.ShoppingCart
 *  com.example.mall.exception.BusinessException
 *  com.example.mall.mapper.OrderItemMapper
 *  com.example.mall.mapper.OrdersMapper
 *  com.example.mall.mapper.ProductMapper
 *  com.example.mall.mapper.ShoppingCartMapper
 *  com.example.mall.order.OrderAsyncService
 *  com.example.mall.order.OrderVersionV5Service
 *  com.example.mall.util.OrderNoUtil
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 */
package com.example.mall.order;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.common.Constants;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.ShoppingCartMapper;
import com.example.mall.order.OrderAsyncService;
import com.example.mall.util.OrderNoUtil;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class OrderVersionV5Service {
    private static final Logger log = LoggerFactory.getLogger(OrderVersionV5Service.class);
    private final ShoppingCartMapper cartMapper;
    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderAsyncService orderAsyncService;

    @Transactional(rollbackFor={Exception.class})
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        List<ShoppingCart> carts = this.cartMapper.selectList(
                Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getSelected, 1));
        if (CollectionUtils.isEmpty((Collection)carts)) {
            throw new BusinessException(ResultCode.CART_EMPTY);
        }
        String orderNo = OrderNoUtil.timeRandom();
        BigDecimal totalAmount = BigDecimal.ZERO;
        ArrayList<OrderItem> items = new ArrayList<OrderItem>(carts.size());
        for (ShoppingCart cart : carts) {
            Product product = (Product)this.productMapper.selectById((Serializable)cart.getProductId());
            if (product == null) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
            }
            if (!Constants.PRODUCT_ON.equals(product.getStatus())) {
                throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF, "\u3010" + product.getName() + "\u3011\u5df2\u4e0b\u67b6");
            }
            int rows = this.productMapper.deductStock(product.getId(), cart.getQuantity());
            if (rows == 0) {
                Integer stock = this.productMapper.selectStock(product.getId());
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "\u3010" + product.getName() + "\u3011\u5e93\u5b58\u4e0d\u8db3\uff0c\u5f53\u524d\u5e93\u5b58 " + (stock == null ? 0 : stock));
            }
            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductCover(product.getCover());
            item.setPrice(product.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity().intValue())));
            items.add(item);
            totalAmount = totalAmount.add(item.getTotalAmount());
        }
        Orders order = new Orders();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setPayStatus(Constants.PAY_STATUS_UNPAID);
        order.setOrderStatus(Constants.ORDER_STATUS_UNPAID);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        this.ordersMapper.insert(order);
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            this.orderItemMapper.insert(item);
        }
        ArrayList<Long> cartIds = new ArrayList<Long>(carts.size());
        for (ShoppingCart cart : carts) {
            cartIds.add(cart.getId());
        }
        this.cartMapper.deleteBatchByIds(cartIds, userId);
        log.info("\u4e0b\u5355\u6210\u529f(V5 \u539f\u5b50\u6263\u51cf) | orderNo={} | userId={} | \u91d1\u989d={}", new Object[]{orderNo, userId, totalAmount});
        return order;
    }

    public OrderVersionV5Service(ShoppingCartMapper cartMapper, ProductMapper productMapper, OrdersMapper ordersMapper, OrderItemMapper orderItemMapper, OrderAsyncService orderAsyncService) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderAsyncService = orderAsyncService;
    }
}

