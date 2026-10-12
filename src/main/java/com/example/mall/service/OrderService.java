package com.example.mall.service;

import com.example.mall.common.PageResult;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;

/**
 * 订单服务
 */
public interface OrderService {

    Orders submit(Long userId, OrderSubmitDTO dto);

    PageResult<Orders> myPage(Long userId, OrderQueryDTO query);

    Orders detail(Long userId, String orderNo);

    void pay(Long userId, String orderNo);

    void cancel(Long userId, String orderNo);
}