package com.example.mall.order.oversell;

import com.example.mall.common.ResultCode;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ============================================================================
 * 【V1】无锁 —— 读库存 → Java 判断 → 直接扣减（错误示范，超卖的根源）
 * ============================================================================
 *
 * <p>"查 - 判 - 扣"三步在 Java 层分开执行，中间<b>没有任何互斥</b>：
 * 20 个线程可以同时读到库存 10、同时通过判断、然后各自把库存减 1 ——
 * 明明只减了 10 次却可能成功 20 单，库存直接干成负数。</p>
 *
 * <p>这就是<b>竞态条件（Race Condition）</b>最典型的样子：
 * 代码单线程看逻辑完全正确，一旦并发，"检查"和"行动"之间的时间窗口
 * 就成了漏洞。第 13 课时用它开局，让超卖先发生，再谈怎么修。</p>
 *
 * <p>故意不加 {@code @Transactional}：每一句 SQL 自动提交，
 * 让问题暴露得最纯粹（加了事务也救不了它，判断在 Java 里就不对）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellV1Service {

    private final ProductMapper productMapper;
    private final OversellSupport support;

    public Orders submit(Long userId, Long productId) {
        // ① 读库存（无锁快照读）
        Integer stock = productMapper.selectStock(productId);
        if (stock == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }

        // ② 教学开关：人为放大"读"和"判"之间的窗口
        support.raceWindow();

        // ③ Java 层判断 —— 并发下 20 个线程都能通过这个 if
        if (stock < 1) {
            log.info("【V1】库存不足，拒绝下单 | productId={} | 读到的库存={}", productId, stock);
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        // ④ 无条件扣减 —— 就是这一句把库存扣成负数的
        productMapper.deductStockWithoutCheck(productId, 1);

        return support.createOrder(userId, support.requireProduct(productId));
    }
}
