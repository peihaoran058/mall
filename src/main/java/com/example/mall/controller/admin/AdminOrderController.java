package com.example.mall.controller.admin;

import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.entity.Orders;
import com.example.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台订单管理（第18课时）。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /** 全部订单分页（可按状态、订单号筛选） */
    @GetMapping("/page")
    public Result<PageResult<Orders>> page(OrderQueryDTO query) {
        return Result.success(orderService.adminPage(query));
    }

    /** 订单详情 */
    @GetMapping("/{orderNo}")
    public Result<Orders> detail(@PathVariable String orderNo) {
        return Result.success(orderService.adminDetail(orderNo));
    }

    /** 修改订单状态：1 待发货 → 2 已发货 → 3 已完成 */
    @PutMapping("/{orderNo}/status")
    public Result<Void> updateStatus(@PathVariable String orderNo, @RequestParam Integer orderStatus) {
        orderService.adminUpdateStatus(orderNo, orderStatus);
        return Result.success("操作成功", null);
    }
}
