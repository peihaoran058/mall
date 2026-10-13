package com.example.mall.order.oversell;

import com.example.mall.common.ResultCode;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * 【V2】事务内加锁 —— @Transactional + synchronized 写在同一个方法上
 * ============================================================================
 *
 * <p><b>这是最高频的面试题：</b>"我加了 synchronized 为什么还超卖？"</p>
 *
 * <p>看代码好像没问题：又加锁又加事务。但锁是加在<b>方法</b>上的，
 * 事务是 Spring 通过 AOP 在方法<b>外层</b>开启和提交的。真正执行顺序是：</p>
 *
 * <pre>
 *   开启事务 → 获得锁 → 读库存 → 判断 → 扣减 → 释放锁 → 提交事务
 *                                    ↑                    ↑
 *                              锁在这里释放          事务在这里才提交
 * </pre>
 *
 * <p>锁先释放、事务后提交 —— 锁<b>根本没包住事务</b>。
 * 线程 A 刚释放锁，事务还没提交；线程 B 拿到锁去读库存，
 * 读到的还是<b>旧值</b>（A 的扣减对 B 不可见），判断照样通过。超卖依旧。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellV2Service {

    private final ProductMapper productMapper;
    private final OversellSupport support;

    /** ★ BUG 就在这一行：synchronized 和 @Transactional 挤在同一个方法上 */
    @Transactional(rollbackFor = Exception.class)
    public synchronized Orders submit(Long userId, Long productId) {
        Integer stock = productMapper.selectStock(productId);
        if (stock == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }

        support.raceWindow();

        if (stock < 1) {
            log.info("【V2】库存不足，拒绝下单 | productId={} | 读到的库存={}", productId, stock);
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        productMapper.deductStockWithoutCheck(productId, 1);

        return support.createOrder(userId, support.requireProduct(productId));
    }
}
