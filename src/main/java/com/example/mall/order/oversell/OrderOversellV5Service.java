package com.example.mall.order.oversell;

import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.common.ResultCode;
import com.example.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * 【V5】数据库原子扣减 —— 一条 UPDATE 解决战斗
 * 缺点：数据库原子扣减的局限：**并发低，高并发下大量锁等待、容易超时；仅解决超卖，不能防重复下单；长事务加剧锁竞争；跨分库分表无法原子执行；数据库本身难以扛秒杀洪峰。**
 * ============================================================================
 *
 * <pre>
 * UPDATE product SET stock = stock - 1, sales = sales + 1
 * WHERE id = ? AND stock &gt;= 1
 * </pre>
 *
 * <p>把 V1 里"Java 层的判断"塞回 SQL 的 WHERE 条件，"判断"和"扣减"
 * 就合并成了一条语句，由 InnoDB 的<b>行锁</b>保证原子性：
 * 20 个线程同时 UPDATE 同一行，数据库会把它们排队串行执行，
 * 每个线程要么扣成功（rows=1），要么发现库存不够（rows=0）。
 * <b>不需要任何应用层锁，天然集群安全。</b></p>
 *
 * <p>结论一句话：能用一条 SQL 解决的问题，不要引入分布式锁。
 * Redis 锁留给"扣库存之外还要做一堆事"的复杂场景。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellV5Service {

    private final ProductMapper productMapper;
    private final OversellSupport support;

    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, Long productId) {
        // ★ 判断 + 扣减在同一条 UPDATE 里，rows=0 就是库存不足
        int rows = productMapper.deductStock(productId, 1);
        if (rows == 0) {
            log.info("【V5】原子扣减失败（库存不足）| productId={}", productId);
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        Product product = support.requireProduct(productId);
        return support.createOrder(userId, product);
    }
}
