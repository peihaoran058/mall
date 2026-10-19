package com.example.mall.service.impl;

import com.example.mall.common.ResultCode;
import com.example.mall.entity.OrderLog;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderLogMapper;
import com.example.mall.service.OrdersLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.Future;

@Service
@Slf4j

public class OrdersLogServiceImpl implements OrdersLogService {

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Override
    // 为什么不使用SpringBoot默认的线程池
    // 1. 线程不会复用，每次都会开启一个新的线程执行任务

    /**
     * <p>
     *     <b>@Async 生效的三个前提（缺一不可）：</b>
     * </p>
     * <ol>
     *     <li>启动类或配置类上有 {@code @EnableAsync}</li>
     *     <li>方法必须是 <b>public</b>，且只能通过<b>注入的 Bean</b> 调用（同类自调用无效）</li>
     *     <li>返回值只能是 {@code void} 或 {@code Future}，返回其他类型拿不到异步结果</li>
     * </ol>
     */
    @Async("mallExecutor")
    public void insertOrderLog(String orderNo) {
        try{
            OrderLog ordersLog = new OrderLog();
            ordersLog.setOrderNo(orderNo);
            ordersLog.setContent("订单创建成功");
            ordersLog.setThreadName(Thread.currentThread().getName());
            log.info("订单日志：{}", ordersLog);
            orderLogMapper.insert(ordersLog);
        }catch (Exception e){
            throw new BusinessException(ResultCode.ORDER_LOG_ERROR);
        }

    }
}
