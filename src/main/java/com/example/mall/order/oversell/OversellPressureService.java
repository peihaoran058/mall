package com.example.mall.order.oversell;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mall.dto.OversellPressureDTO;
import com.example.mall.entity.Orders;
import com.example.mall.entity.SysUser;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.util.MD5Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * 后端内置并发压测（第 13 课时）
 * ============================================================================
 *
 * <p><b>为什么要压在服务端，而不是让浏览器发 20 个请求？</b></p>
 * <ul>
 *     <li>浏览器对同一域名的 HTTP/1.1 并发连接上限通常是 6 ——
 *         20 个 fetch 实际只有 6 个在跑，剩下 14 个在排队，
 *         "并发"变成"分批串行"，V1 的竞态窗口根本撞不上，压不出超卖。</li>
 *     <li>浏览器还有优先级调度、缓存、CORS 预检等干扰，结果没法解释。</li>
 * </ul>
 *
 * <p>老版方案是让 Node 网关（server/index.js）用 Promise.all 制造并发；
 * 现在直接由 SpringBoot 用<b>线程池 + CountDownLatch</b> 在进程内起 N 个线程
 * 同时调下单方法 —— 对学生来说反而更好：这一版压测代码本身就是
 * 第 13 课时的并发教材（栅栏并发、线程池、Future 收集结果）。</p>
 *
 * <p><b>执行流程：</b>重置库存 → 清理历史订单 → N 个线程在栅栏处待命 →
 * 一声令下同时下单 → 收集每个请求的结果 → 读回库存给判定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OversellPressureService {

    /** 共享压测账号：不下购物车，只用来给订单挂 userId */
    private static final String LOADTEST_USERNAME = "loadtest";

    private final OrderOversellService oversellService;
    private final OversellDataService dataService;
    private final SysUserMapper sysUserMapper;
    private final OrdersMapper ordersMapper;

    public Map<String, Object> run(OversellPressureDTO dto) {
        Long productId = dto.getProductId() == null ? 1L : dto.getProductId();
        int stock = dto.getStock() == null ? 10 : Math.max(0, dto.getStock());
        int concurrency = Math.min(Math.max(dto.getConcurrency() == null ? 20 : dto.getConcurrency(), 2), 100);

        int version = oversellService.currentVersion();
        Long userId = resolveDemoUser();

        /* ---------- ① 重置现场 ---------- */
        dataService.resetStock(productId, stock);
        dataService.cleanOrders();

        long startedAt = System.currentTimeMillis();

        /* ---------- ② N 个线程在栅栏前待命 ---------- */
        // CountDownLatch 是"发令枪"：所有线程先 await 阻塞住，
        // 主线程 countDown 的一瞬间大家同时起跑 —— 这才是真并发，
        // 如果在线程里直接开跑，线程创建有先后，竞态窗口会错开。
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Map<String, Object>>> futures = new ArrayList<>(concurrency);

        for (int i = 1; i <= concurrency; i++) {
            final int index = i;
            futures.add(pool.submit(() -> doSubmit(index, userId, productId, startGate)));
        }
        startGate.countDown();

        /* ---------- ③ 收集结果 ---------- */
        List<Map<String, Object>> results = new ArrayList<>(concurrency);
        for (Future<Map<String, Object>> future : futures) {
            try {
                results.add(future.get(2, TimeUnit.MINUTES));
            } catch (Exception e) {
                log.error("压测任务异常", e);
                Map<String, Object> failed = new LinkedHashMap<>(4);
                failed.put("ok", false);
                failed.put("code", 500);
                failed.put("msg", "压测线程异常：" + e.getMessage());
                results.add(failed);
            }
        }
        pool.shutdown();

        for (int i = 0; i < results.size(); i++) {
            results.get(i).put("index", i + 1);
        }
        results.sort(Comparator.comparingInt(m -> (Integer) m.get("index")));

        int successCount = (int) results.stream().filter(r -> Boolean.TRUE.equals(r.get("ok"))).count();
        long burstElapsed = System.currentTimeMillis() - startedAt;

        /* ---------- ④ 读回库存给判定 ---------- */
        Map<String, Object> stockInfo = dataService.stockInfo(productId);
        int stockAfter = (int) stockInfo.get("stock");

        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("version", version);
        data.put("versionDescription", oversellService.describe(version));
        data.put("productId", productId);
        data.put("concurrency", concurrency);
        data.put("successCount", successCount);
        data.put("failedCount", results.size() - successCount);
        data.put("stockBefore", stock);
        data.put("stockAfter", stockAfter);
        data.put("oversell", stockAfter < 0);
        data.put("elapsedMs", System.currentTimeMillis() - startedAt);
        data.put("burstElapsedMs", burstElapsed);
        data.put("stockInfo", stockInfo);
        data.put("results", results);

        log.info("【课堂实验】并发压测完成 | V{} | 商品 {} | 并发 {} | 成功 {} | 失败 {} | 库存 {} → {}{}",
                version, productId, concurrency, successCount, results.size() - successCount,
                stock, stockAfter, stockAfter < 0 ? "（超卖！）" : "");
        return data;
    }

    /** 单个压测请求：等发令枪 → 下单 → 记录结果（成功失败都不抛，保证 future 一定有值） */
    private Map<String, Object> doSubmit(int index, Long userId, Long productId, CountDownLatch startGate) {
        Map<String, Object> row = new LinkedHashMap<>(8);
        long start = System.currentTimeMillis();
        try {
            startGate.await();
            oversellService.submit(userId, productId);
            row.put("ok", true);
            row.put("code", 200);
            row.put("msg", "下单成功");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            row.put("ok", false);
            row.put("code", 500);
            row.put("msg", "线程被中断");
        } catch (BusinessException e) {
            row.put("ok", false);
            row.put("code", e.getCode());
            row.put("msg", e.getMessage());
        } catch (Exception e) {
            row.put("ok", false);
            row.put("code", 500);
            row.put("msg", e.getClass().getSimpleName() + "：" + e.getMessage());
        }
        row.put("elapsedMs", System.currentTimeMillis() - start);
        return row;
    }

    /**
     * 找（找不到就建）共享压测账号。
     * <p>压测不走购物车，所有并发请求共用这一个账号完全没问题；
     * 订单靠 remark = loadtest 标记识别，清理时不会误伤真实数据。</p>
     */
    private Long resolveDemoUser() {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, LOADTEST_USERNAME));
        if (user != null) {
            return user.getId();
        }
        user = new SysUser();
        user.setUsername(LOADTEST_USERNAME);
        user.setPassword(MD5Util.md5("123456"));
        user.setNickname("并发压测账号");
        sysUserMapper.insert(user);
        log.info("【课堂实验】已创建共享压测账号 | id={} | username={}", user.getId(), user.getUsername());
        return user.getId();
    }
}
