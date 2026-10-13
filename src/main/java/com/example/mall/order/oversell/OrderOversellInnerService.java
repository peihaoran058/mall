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
 * 事务包住的"落库内层"—— V3 / V4 共用的下单实现
 * ============================================================================
 *
 * <p>单独拆成一个 Bean 是<b>故意的</b>：事务注解要生效，
 * 必须从外部穿过 Spring 代理调用（第 11 课时事务失效讲过的 self-invocation 坑）。
 * V3、V4 的外层类拿到锁之后调这里，锁的范围就包住了整个事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellInnerService {

    private final ProductMapper productMapper;
    private final OversellSupport support;

    /** 事务内的完整下单：读库存 → 判断 → 扣减 → 落单 */
    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, Long productId) {
        Integer stock = productMapper.selectStock(productId);
        if (stock == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }

        support.raceWindow();

        if (stock < 1) {
            log.info("【内层】库存不足，拒绝下单 | productId={} | 读到的库存={}", productId, stock);
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        productMapper.deductStockWithoutCheck(productId, 1);

        Product product = support.requireProduct(productId);
        return support.createOrder(userId, product);
    }
}
