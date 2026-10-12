/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.example.mall.common.PageResult
 *  com.example.mall.common.Result
 *  com.example.mall.controller.admin.AdminOrderController
 *  com.example.mall.dto.OrderQueryDTO
 *  com.example.mall.entity.Orders
 *  com.example.mall.service.OrderService
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package com.example.mall.controller.admin;

import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.entity.Orders;
import com.example.mall.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/admin/order"})
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping(value={"/page"})
    public Result<PageResult<Orders>> page(OrderQueryDTO query) {
        return Result.success(this.orderService.adminPage(query));
    }

    @GetMapping(value={"/{orderNo}"})
    public Result<Orders> detail(@PathVariable String orderNo) {
        return Result.success(this.orderService.adminDetail(orderNo));
    }

    @PutMapping(value={"/{orderNo}/status"})
    public Result<Void> updateStatus(@PathVariable String orderNo, @RequestParam Integer orderStatus) {
        this.orderService.adminUpdateStatus(orderNo, orderStatus);
        return Result.success((String)"\u64cd\u4f5c\u6210\u529f", null);
    }

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }
}

