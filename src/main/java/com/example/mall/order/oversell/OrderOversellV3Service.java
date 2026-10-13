package com.example.mall.order.oversell;

import com.example.mall.entity.Orders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ============================================================================
 * 【V3】单机锁 —— synchronized 移到事务外层（单机正确版）
 * ============================================================================
 *
 * <p>修复思路：把锁从"事务里面"挪到"事务外面"—— 外层方法只管抢锁，
 * 抢到之后才进事务，事务提交完（内层方法返回）锁才释放：</p>
 *
 * <pre>
 *   获得锁 → 开启事务 → 读 → 判 → 扣 → 落单 → 提交事务 → 释放锁
 *          └────────── 锁完整包住了事务 ──────────┘
 * </pre>
 *
 * <p>这样 B 拿到锁时，A 的事务<b>一定已经提交</b>，读到的库存必然是最新值。
 * 但 synchronized 只在<b>单个 JVM</b> 内有效 —— 商城一旦部署两个实例，
 * 两台机器各锁各的，超卖照样发生。这就是它只能算"半成品修复"的原因，
 * 也是下一版（V4 Redis 分布式锁）要解决的问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellV3Service {

    private final OrderOversellInnerService innerService;

    /** ★ 锁在外层（无事务的 Bean 上），内层才开事务 —— 和 V2 对比着讲 */
    public synchronized Orders submit(Long userId, Long productId) {
        return innerService.submit(userId, productId);
    }
}
