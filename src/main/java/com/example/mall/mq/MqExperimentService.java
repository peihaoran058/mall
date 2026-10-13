package com.example.mall.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mall.config.MallProperties;
import com.example.mall.entity.Orders;
import com.example.mall.entity.SysUser;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.mq.v8.StockRedisService;
import com.example.mall.order.oversell.OversellDataService;
import com.example.mall.order.oversell.OversellSupport;
import com.example.mall.util.MD5Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
 * 洪峰实验（第20课时）—— 把 MQ 的削峰和超卖问题一次讲完
 * ============================================================================
 * 【讲解顺序 ⑨ · 支撑】看板与实验支撑类，随堂用到再翻（见 mq 包导读）。
 *
 * <p><b>实验怎么做的（前端"开始洪峰实验"按钮背后的四步）：</b></p>
 * <ol>
 *     <li>把商品库存重置为「初始库存」，清空队列、计数器归零；</li>
 *     <li>用线程池 + CountDownLatch <b>同时</b>投递「洪峰请求数」条下单消息；</li>
 *     <li>等消费端把队列吃干净（按 messageId 记账的在途数归零）；</li>
 *     <li>读回数据库库存，看有没有变成负数。</li>
 * </ol>
 *
 * <p><b>结果里要对比两组数字：</b></p>
 * <ul>
 *     <li><b>生产耗时</b>（用户实际等了多久，只是"消息进队列"）vs
 *         <b>消费耗时</b>（真正下单花了多久）→ 这就是<b>削峰</b>；</li>
 *     <li><b>成功单数</b> vs <b>初始库存</b> → 这就是<b>有没有超卖</b>。</li>
 * </ul>
 *
 * <p><b>三版预期（课堂上按这个顺序演示）：</b></p>
 * <table border="1">
 *     <caption>洪峰实验预期结果</caption>
 *     <tr><th>版本</th><th>投递</th><th>最终库存</th><th>结论</th></tr>
 *     <tr><td>V6</td><td>全部进队列</td><td>负数</td>
 *         <td>❌ 上了 MQ 照样超卖 —— MQ 只负责排队，不负责并发控制</td></tr>
 *     <tr><td>V7</td><td>全部进队列</td><td>0</td>
 *         <td>✅ 不超卖，但洪峰全量打到消费端（消费耗时被拉长）</td></tr>
 *     <tr><td>V8</td><td><b>只有抢到名额的进队列</b></td><td>0</td>
 *         <td>✅ 不超卖，且绝大多数请求在 Redis 就被挡掉 —— 数据库压根没感受到洪峰</td></tr>
 * </table>
 *
 * <p>⚠️ 为什么不用浏览器发 20 个请求来压？<br>
 * 浏览器对同一域名的 HTTP/1.1 并发连接上限通常是 6，20 个 fetch 实际只有 6 个在跑，
 * "并发"变成"分批串行"，竞态窗口根本撞不上。所以这里和
 * {@code OversellPressureService} 一样，用<b>服务端线程池 + 栅栏</b>制造真并发。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqExperimentService {

    /** 投递线程池上限：洪峰请求数最大 200，但没必要开 200 个线程去做"发消息"这种轻活 */
    private static final int MAX_PRODUCER_THREADS = 32;

    /** 共享压测账号（和并发超卖实验同一个），订单靠 remark = loadtest 标记识别 */
    private static final String LOADTEST_USERNAME = "loadtest";

    private final MallProperties mallProperties;
    private final MqModeService mqModeService;
    private final MqStatusService mqStatusService;
    private final OrderMqService orderMqService;
    private final StockRedisService stockRedisService;
    private final OrderMessageTracker tracker;
    private final OversellDataService oversellDataService;
    private final OrdersMapper ordersMapper;
    private final SysUserMapper sysUserMapper;
    private final AmqpAdmin amqpAdmin;

    /**
     * 跑一轮洪峰实验。
     *
     * @param productId 商品 id（默认 1）
     * @param stock     初始库存（默认 10）
     * @param count     洪峰请求数（默认 20，建议 &gt; 库存才压得出超卖）
     */
    public Map<String, Object> flood(Long productId, Integer stock, Integer count) {
        Long pid = productId == null ? 1L : productId;
        int initStock = stock == null ? 10 : Math.max(1, Math.min(500, stock));
        int requests = count == null ? 20 : Math.max(2, Math.min(200, count));

        int mode = mqModeService.current();
        Long userId = resolveDemoUser();

        /* ---------- ① 重置现场：队列、计数器、库存、订单 ---------- */
        mqStatusService.purge();                                   // 清队列 + 计数器/结果缓存归零
        Map<String, Object> before = oversellDataService.resetStock(pid, initStock);  // 库存归位、销量清零
        oversellDataService.cleanOrders();                          // 清掉上一轮的压测订单

        /* ---------- ② 库存副本：V8 预热 Redis，V6/V7 清掉避免旧值混淆 ---------- */
        boolean preDeduct = mqModeService.preDeductEnabled();
        if (preDeduct) {
            stockRedisService.warmup(pid, initStock);
        } else {
            stockRedisService.clear(pid);
        }
        String productName = String.valueOf(before.get("productName"));

        List<Map<String, Object>> steps = new ArrayList<>(6);
        steps.add(step("① 重置现场",
                "库存重置为 " + initStock + "，销量清零，业务队列/死信队列清空，计数器归零"));
        steps.add(step("② 库存副本",
                preDeduct
                        ? "V8：把库存预热进 Redis（" + stockRedisService.stockKey(pid) + " = " + initStock + "），入口将用它做原子预扣"
                        : "V" + mode + "：入口无门禁，清掉 Redis 里的旧库存副本，所有请求直接进队列"));

        /* ---------- ③ 并发投递（生产阶段） ---------- */
        long produceStart = System.currentTimeMillis();
        Map<String, Object> burst = burstSubmit(pid, requests, userId);
        long produceMs = System.currentTimeMillis() - produceStart;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) burst.get("results");
        int sentOk = (int) burst.get("okCount");
        int gateRejected = (int) burst.get("gateRejected");
        int submitError = (int) burst.get("error");

        steps.add(step("③ 并发投递 " + requests + " 条消息",
                "服务端线程池 + CountDownLatch 真并发（浏览器并发上限只有 6，压不出效果）。"
                        + "投递成功 " + sentOk + " 条，被 Redis 预扣挡掉 " + gateRejected + " 条，"
                        + "其他异常 " + submitError + " 条，耗时 " + produceMs + "ms"));

        /* ---------- ④ 等消费端吃干净（消费阶段） ---------- */
        Long timeout = mallProperties.getMq().getFloodTimeoutMillis();
        long consumeStart = System.currentTimeMillis();
        boolean drained = tracker.awaitDrain(timeout == null ? 30000L : timeout);
        long consumeMs = System.currentTimeMillis() - consumeStart;

        steps.add(step("④ 等待消费排空",
                (drained ? "在途消息已全部处理完" : "⚠️ 等待超时，仍有在途消息，下面的库存可能不是最终值")
                        + "，消费耗时 " + consumeMs + "ms（并发 "
                        + mallProperties.getMq().getConsumerConcurrency() + "~"
                        + mallProperties.getMq().getConsumerMaxConcurrency() + "）"));

        /* ---------- ⑤ 读回库存 / 订单 / 死信，做判定 ---------- */
        Map<String, Object> after = oversellDataService.stockInfo(pid);
        int stockAfter = ((Number) after.get("stock")).intValue();
        boolean oversell = stockAfter < 0;
        long orderRows = countLoadtestOrders();
        int deadLettered = deadQueueDepth();

        steps.add(step("⑤ 读回结果判定",
                "库存 " + initStock + " → " + stockAfter + "，订单表新增 " + orderRows
                        + " 笔，死信堆积 " + deadLettered + " 条 → "
                        + (oversell ? "❌ 出现超卖（库存为负）" : "✅ 未超卖")));

        Map<String, Object> trackerSnapshot = tracker.snapshot();

        Map<String, Object> data = new LinkedHashMap<>(32);
        data.put("mode", mode);
        data.put("modeDescription", mqModeService.describe(mode));
        data.put("productId", pid);
        data.put("productName", productName);
        data.put("concurrency", requests);

        data.put("sent", trackerSnapshot.get("sent"));
        data.put("consumed", trackerSnapshot.get("consumed"));
        data.put("successCount", trackerSnapshot.get("persisted"));
        data.put("failedCount", trackerSnapshot.get("stockNotEnough"));
        data.put("duplicatedCount", trackerSnapshot.get("duplicate"));
        data.put("deadLetteredCount", deadLettered);

        data.put("stockBefore", initStock);
        data.put("stockAfter", stockAfter);
        data.put("oversell", oversell);
        data.put("produceMs", produceMs);
        data.put("consumeMs", consumeMs);
        data.put("orderRows", orderRows);
        data.put("drained", drained);

        /* ---------- 附加字段：V8 专属的两个数，前端可选展示 ---------- */
        data.put("gateRejectedCount", gateRejected);
        data.put("compensatedCount", trackerSnapshot.get("compensated"));
        data.put("errorCount", trackerSnapshot.get("error"));
        data.put("redisStockAfter", preDeduct ? stockRedisService.getStock(pid) : null);
        data.put("requestResults", results);
        data.put("steps", steps);
        data.put("logs", tracker.logs());

        log.info("【课堂实验】MQ 洪峰实验完成 | V{} | 商品 {} | 洪峰 {} | 投递 {}（挡掉 {}）| 成功 {} | 库存 {} → {}{}",
                mode, pid, requests, trackerSnapshot.get("sent"), gateRejected,
                trackerSnapshot.get("persisted"), initStock, stockAfter, oversell ? "（超卖！）" : "");
        return data;
    }

    /* ==================================================================
     * 并发投递：线程池 + 栅栏
     * ================================================================== */

    private Map<String, Object> burstSubmit(Long productId, int requests, Long userId) {
        CountDownLatch startGate = new CountDownLatch(1);
        int poolSize = Math.min(requests, MAX_PRODUCER_THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<Future<Map<String, Object>>> futures = new ArrayList<>(requests);

        for (int i = 1; i <= requests; i++) {
            final int index = i;
            futures.add(pool.submit(() -> doSubmit(index, productId, userId, startGate)));
        }
        // 发令枪：所有线程先 await 阻塞住，countDown 的一瞬间同时起跑 —— 这才是真并发。
        // 如果在线程里直接开跑，线程创建有先后，竞态窗口就错开了。
        startGate.countDown();

        List<Map<String, Object>> results = new ArrayList<>(requests);
        int okCount = 0;
        int gateRejected = 0;
        int error = 0;

        for (Future<Map<String, Object>> future : futures) {
            Map<String, Object> row;
            try {
                row = future.get(2, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("洪峰投递任务异常", e);
                row = new LinkedHashMap<>(4);
                row.put("ok", false);
                row.put("code", 500);
                row.put("msg", "投递线程异常：" + e.getMessage());
            }
            results.add(row);
            if (Boolean.TRUE.equals(row.get("ok"))) {
                okCount++;
            } else if (Integer.valueOf(3003).equals(row.get("code"))) {
                // 3003 = 库存不足
                gateRejected++;
            } else {
                error++;
            }
        }
        pool.shutdown();

        results.sort(Comparator.comparingInt(m -> (Integer) m.get("index")));

        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("okCount", okCount);
        data.put("gateRejected", gateRejected);
        data.put("error", error);
        data.put("results", results);
        return data;
    }

    /**
     * 单次投递：等发令枪 → 调秒杀入口 → 记录结果。
     *
     * <p>注意这里调的是<b>真正的秒杀入口 {@link OrderMqService#submit}</b>，
     * 不是"绕过入口直接往队列塞"—— 只有走同一条代码路径，
     * V8 的"入口预扣挡掉绝大多数请求"这个结论才成立。</p>
     */
    private Map<String, Object> doSubmit(int index, Long productId, Long userId, CountDownLatch startGate) {
        Map<String, Object> row = new LinkedHashMap<>(8);
        long start = System.currentTimeMillis();
        try {
            startGate.await();
            Map<String, Object> submit = orderMqService.submit(
                    userId, productId, 1,
                    "MQ 洪峰压测收货人", "13800000000", "压测地址（MQ 洪峰实验）",
                    OversellSupport.LOADTEST_REMARK);
            row.put("ok", true);
            row.put("code", 200);
            row.put("msg", "已投递");
            row.put("orderNo", submit.get("orderNo"));
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
        row.put("index", index);
        row.put("elapsedMs", System.currentTimeMillis() - start);
        return row;
    }

    /* ==================== 内部工具 ==================== */

    private Map<String, Object> step(String step, String detail) {
        Map<String, Object> row = new LinkedHashMap<>(2);
        row.put("step", step);
        row.put("detail", detail);
        return row;
    }

    /** 本轮压测产生的订单笔数（remark = loadtest，上面已经清过，所以剩下的都是这一轮的） */
    private long countLoadtestOrders() {
        Long rows = ordersMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getRemark, OversellSupport.LOADTEST_REMARK));
        return rows == null ? 0L : rows;
    }

    /** 死信队列积压数：>0 说明有消息重试 3 次仍然失败，需要人工介入 */
    private int deadQueueDepth() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(mallProperties.getMq().getDlqQueue());
            return info == null ? 0 : info.getMessageCount();
        } catch (Exception e) {
            log.warn("查询死信队列深度失败 | {}", e.getMessage());
            return 0;
        }
    }

    /** 找（找不到就建）共享压测账号，和 {@code OversellPressureService} 用的是同一个 */
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
