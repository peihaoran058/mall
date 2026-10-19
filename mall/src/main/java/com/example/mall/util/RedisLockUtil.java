package com.example.mall.util;

import com.example.mall.common.ResultCode;
import com.example.mall.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 手写 Redis 分布式锁（第16课时）。
 *
 * <p><b>三个必须做对的点（错一个锁就不可靠）：</b></p>
 * <ol>
 *     <li><b>加锁必须是原子的</b>：{@code setnx} + {@code expire} 分两步发的话，
 *         中间宕机就会出现"永远不过期的死锁"。必须用 {@code SET key value NX EX seconds}
 *         一条命令搞定，也就是 {@code setIfAbsent(key, value, timeout, unit)}。</li>
 *     <li><b>value 必须是唯一标识</b>（这里用 UUID）：否则 A 的业务超时后锁自动过期，
 *         B 拿到锁，A 执行完把 B 的锁删了 —— 这就是"误删别人的锁"。</li>
 *     <li><b>释放锁必须用 Lua 脚本</b>：先 get 判断再 del 是两条命令，中间有时间窗口，
 *         必须在 Redis 里原子执行。</li>
 * </ol>
 *
 * <p><b>⚠️ 课堂必须强调：这套手写锁只用于理解原理，生产环境用 Redisson。</b><br>
 * 它解决不了的场景：</p>
 * <ul>
 *     <li>业务执行时间 &gt; 锁过期时间 → 锁提前释放，别人进来，超卖依旧发生（Redisson 的看门狗可自动续期）</li>
 *     <li>Redis 主从切换时锁可能丢失（需要 RedLock 或直接上 ZooKeeper/etcd）</li>
 *     <li>不可重入、不支持公平锁、不支持等待队列</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisLockUtil {

    /**
     * 释放锁的 Lua 脚本：只有 value 对得上（说明是自己的锁）才删除。
     * 返回 1 表示删除成功，0 表示锁已不是自己的（已过期被别人拿走）。
     */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLockUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁，拿不到就自旋等待，超时抛业务异常。
     *
     * @param key           锁的 key
     * @param expireSeconds 锁自动过期时间（秒），必须大于业务最长执行时间
     * @param waitMillis    获取锁的最大等待毫秒数
     * @return 锁的唯一标识，释放锁时必须原样传回
     */
    public String tryLock(String key, long expireSeconds, long waitMillis) {
        String requestId = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitMillis;
        int retry = 0;

        while (System.currentTimeMillis() < deadline) {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, requestId, expireSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.debug("获取锁成功 | key={} | requestId={} | 第{}次尝试", key, requestId, retry + 1);
                return requestId;
            }
            retry++;
            try {
                // 自旋间隔。真实项目里应该用"递增退避"，避免大量线程同时醒来造成惊群
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.LOCK_ACQUIRE_FAIL);
            }
        }

        log.warn("获取锁失败，等待超时 | key={} | 尝试次数={}", key, retry);
        throw new BusinessException(ResultCode.LOCK_ACQUIRE_FAIL);
    }

    /**
     * 释放锁（Lua 原子删除）。
     * <p>一定放在 {@code finally} 里，否则业务抛异常锁就永远不释放了。</p>
     */
    public boolean unlock(String key, String requestId) {
        if (requestId == null) {
            return false;
        }
        try {
            Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), requestId);
            boolean released = result != null && result > 0;
            if (!released) {
                // 走到这里通常是"业务执行太久，锁已经自动过期"，是超卖的隐患信号，必须打日志告警
                log.warn("释放锁失败：锁已过期或已被他人持有 | key={} | requestId={}", key, requestId);
            }
            return released;
        } catch (Exception e) {
            log.error("释放锁异常 | key={}", key, e);
            return false;
        }
    }
}
