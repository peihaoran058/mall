package com.example.mall.order.oversell;

import com.example.mall.config.MallProperties;
import com.example.mall.entity.Orders;
import com.example.mall.util.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ============================================================================
 * 【V4】Redis 分布式锁 —— SET NX EX + UUID 标识 + Lua 原子释放（集群正确版）
 * ============================================================================
 *
 * <p>把 V3 的 synchronized 换成 Redis 锁：所有实例竞争<b>同一把</b>锁，
 * 多实例部署也安全。锁的三个要点（原子加锁 / 唯一标识 / Lua 释放）
 *
 * <p>代价也要讲清楚：每次下单多一次 Redis 往返；要考虑锁超时、续期、误删；
 * 手写锁只用于理解原理，生产环境直接用 Redisson。</p>
 *
 * 实例A(请求1)                Redis                  实例B(请求2)
 *     │                         │                        │
 *     │  1.SET lock:order:1001 uuid-A NX EX 30s           │
 *     │────────────────────────>│                        │
 *     │                         │                        │
 *     │  2.返回 true（加锁成功）   │                        │
 *     │<────────────────────────│                        │
 *     │                         │                        │
 *     │ 执行业务（扣库存/下单）     │                        │
 *     │                         │                        │
 *     │                         │  3.SET lock:order:1001 uuid-B NX EX 30s
 *     │                         │<──────────────────────│
 *     │                         │  4.返回 false(锁存在)  │
 *     │                         │──────────────────────>│
 *     │                         │                        │
 *     │                         │ B自旋等待，循环重试抢锁    │
 *     │                         │                        │
 *     │业务执行完毕               │                        │
 *     │ 5.Lua脚本：GET判断+DEL    │                        │
 *     │────────────────────────>│                        │
 *     │                         │校验value=uuid-A，删除key │
 *     │ 6.返回1解锁成功           │                        │
 *     │<────────────────────────│                        │
 *     │                         │                        │
 *     │                         │ B再次尝试SET，拿到锁      │
 *     │                         │……后续执行业务……          │
 *
 * 业务超时锁过期
 *     实例A                      Redis                   实例C
 *    │                         │                       │
 *    │ SET lock:order:1001 uuidA NX EX30s              │
 *    │────────────────────────>│                       │
 *    │<────────────────────────│ 返回true，拿到锁        │
 *    │                         │                       │
 *    │ 业务执行很慢，超过30s！     │                      │
 *    │                         │ 30s到，key自动过期删除   │
 *    │                         │                       │
 *    │                         │ SET lock:order:1001 uuidC NX EX30s
 *    │                         │<──────────────────────│
 *    │                         │ 返回true，C拿到锁！     │
 *    │                         │──────────────────────>│
 *    │ 业务还没跑完！             │                       │
 *    │ 两个实例同时处理同一订单 → 超卖！                     │
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellV4Service {

    private final RedisLockUtil redisLockUtil;
    private final OrderOversellInnerService innerService;
    private final MallProperties mallProperties;

    public Orders submit(Long userId, Long productId) {
        // 同一件商品一把锁，不同商品互不影响
        String lockKey = "mall:oversell:lock:product:" + productId;
        Long timeout = mallProperties.getOrder().getLockTimeout();
        Long waitMillis = mallProperties.getOrder().getLockWaitMillis();

        String requestId = redisLockUtil.tryLock(lockKey, timeout == null ? 10L : timeout,
                waitMillis == null ? 3000L : waitMillis);
        try {
            // ★ 拿到锁才进事务 —— 和 V3 相同的结构，只是锁的实现换成了 Redis
            return innerService.submit(userId, productId);
        } finally {
            // 必须放 finally：业务抛异常锁也不能留着不释放
            redisLockUtil.unlock(lockKey, requestId);
        }
    }
}
