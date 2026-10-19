package com.example.mall.controller;

import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import com.example.mall.config.MallProperties;
import com.example.mall.dto.OversellPressureDTO;
import com.example.mall.exception.BusinessException;
import com.example.mall.order.OrderAsyncService;
import com.example.mall.order.oversell.OversellDataService;
import com.example.mall.order.oversell.OversellPressureService;
import com.example.mall.order.oversell.OrderOversellService;
import com.example.mall.order.oversell.OversellSupport;
import com.example.mall.order.tx.OrderTxExperimentService;
import com.example.mall.order.tx.OrderTxSubmitService;
import com.example.mall.mq.MqExperimentService;
import com.example.mall.mq.MqModeService;
import com.example.mall.mq.MqStatusService;
import com.example.mall.mq.OrderMqService;
import com.example.mall.mq.v8.StockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * 课堂演示接口集合
 * ============================================================================
 *
 * <p>这些接口<b>不是业务接口</b>，是专门为了"现场制造 BUG、现场验证修复"
 * 而暴露的开关。所以它们在 WebMvcConfig 里被放行（不需要登录），
 * <b>上线前一定要删掉或者加 IP 白名单</b>。</p>
 *
 * <p>建议的课堂演示顺序见 docs/04-错误演示与解决方案.md。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final OrderTxSubmitService orderTxSubmitService;
    private final OrderTxExperimentService orderTxExperimentService;
    private final MallProperties mallProperties;
    private final OrderOversellService orderOversellService;
    private final OversellDataService oversellDataService;
    private final OversellPressureService oversellPressureService;
    private final OrderAsyncService orderAsyncService;

    /* ---- 第20课时：秒杀异步下单（RabbitMQ） ---- */
    private final MqModeService mqModeService;
    private final MqStatusService mqStatusService;
    private final MqExperimentService mqExperimentService;
    private final OrderMqService orderMqService;
    private final StockRedisService stockRedisService;

    /* ==================================================================
     * 一、统一返回 & 全局异常（第2课时）
     * ================================================================== */

    /**
     * 抛业务异常，看统一返回结构：{"code":1001,"msg":"用户名已存在"}
     */
    @GetMapping("/exception/business")
    public Result<Void> businessException() {
        throw new BusinessException(ResultCode.USERNAME_EXISTS);
    }

    /**
     * 抛系统异常，看兜底处理：{"code":500,"msg":"系统繁忙，请稍后重试"}
     */
    @GetMapping("/exception/system")
    public Result<Integer> systemException() {
        // 故意制造空指针，验证 GlobalExceptionHandler 的兜底逻辑
        String nullStr = null;
        return Result.success("永远走不到这里", nullStr.length());
    }

    /* ==================================================================
     * 二、事务失效实验（第 10~11 课时）
     * ------------------------------------------------------------------
     * ⚠️ 这一组<b>不是</b>往日志表插假数据的玩具实验：
     *   它调用的是真实下单链路（OrderTxSubmitService → OrderPersistSupport），
     *   会真的扣 product.stock、真的往 orders/order_item 插单、真的清 shopping_cart。
     *   所以验证方式是查这些<b>业务表</b>，不是查 order_log 里的假记录。
     * ------------------------------------------------------------------
     * 兼容说明：rollback-* 五个接口是给前端演示台（mall-web /admin/demo）留的
     * 旧路径，内部跑的就是 T1~T5 真实下单实验。返回体保持旧格式
     * {scenario, expect, remaining, result}，但 remaining 的含义变了：
     * 旧 = tx_demo 表剩几条；新 = 业务表有没有留下脏数据（0 = 干净，1 = 有脏数据）。
     * ================================================================== */

    /**
     * 切换事务实验版本：value = 1~5（实验版本） / 0（清除覆盖，回到 yml 配置）
     */
    @PostMapping("/tx/mode")
    public Result<Map<String, Object>> txMode(@RequestParam(defaultValue = "0") Integer value) {
        return Result.success(orderTxSubmitService.setMode(value));
    }

    /**
     * 查看当前生效的事务实验版本
     */
    @GetMapping("/tx/state")
    public Result<Map<String, Object>> txState() {
        Map<String, Object> data = new HashMap<>(4);
        int version = orderTxSubmitService.currentVersion();
        data.put("version", version);
        data.put("enabled", version > 0);
        data.put("description", orderTxSubmitService.describe(version));
        return Result.success(data);
    }

    /**
     * 跑一次真实的下单实验：备好购物车 → 下单 → 对比前后数据 → 直接给出判定。
     *
     * <p>返回里会明确告诉你"订单有没有残留、库存有没有被扣、购物车有没有被清"，
     * 并且带一条 {@code checkSql}，学员可以拿去数据库里自己复核。</p>
     */
    @PostMapping("/tx/order")
    public Result<Map<String, Object>> txOrder(@RequestParam(required = false) Long userId,
                                               @RequestParam(defaultValue = "1") Long productId,
                                               @RequestParam(defaultValue = "1") Integer quantity,
                                               @RequestParam(required = false) Integer version) {
        return Result.success(orderTxExperimentService.run(userId, productId, quantity, version));
    }

    /* ---------------- 兼容前端演示台的五个旧路径（内部 = T1~T5 真实下单实验） ---------------- */

    /**
     * 实验一（T1 正确版）：有 rollbackFor，异常整体回滚。期望 remaining = 0
     */
    @PostMapping("/tx/rollback-success")
    public Result<Map<String, Object>> txRollbackSuccess(@RequestParam(defaultValue = "TX-1") String tag) {
        return Result.success(txCompat(1, tag));
    }

    /**
     * 实验二（T2）：漏写 rollbackFor + 抛受检异常 → 不回滚。期望 remaining = 1
     */
    @PostMapping("/tx/rollback-fail")
    public Result<Map<String, Object>> txRollbackFail(@RequestParam(defaultValue = "TX-2") String tag) {
        return Result.success(txCompat(2, tag));
    }

    /**
     * 实验三（T3）：异常被 try-catch 吃掉 → 接口"成功"但数据脏了（最隐蔽）。期望 remaining = 1
     */
    @PostMapping("/tx/rollback-fail-catch")
    public Result<Map<String, Object>> txRollbackFailCatch(@RequestParam(defaultValue = "TX-3") String tag) {
        return Result.success(txCompat(3, tag));
    }

    /**
     * 实验四（T4）：同类 this 自调用 → 事务完全不生效。期望 remaining = 1
     */
    @PostMapping("/tx/rollback-fail-self")
    public Result<Map<String, Object>> txRollbackFailSelf(@RequestParam(defaultValue = "TX-4") String tag) {
        return Result.success(txCompat(4, tag));
    }

    /**
     * 实验五（T5）：@Transactional 写在 private 方法上 → 注解被静默忽略。期望 remaining = 1
     */
    @PostMapping("/tx/rollback-fail-private")
    public Result<Map<String, Object>> txRollbackFailPrivate(@RequestParam(defaultValue = "TX-5") String tag) {
        return Result.success(txCompat(5, tag));
    }

    /**
     * 跑一个版本的真实下单实验，并把结果映射成旧版演示台的返回格式。
     * <p>{@code tag} 参数保留（前端会传 TX-1~TX-4），只进日志，方便对账。</p>
     */
    private Map<String, Object> txCompat(int version, String tag) {
        log.info("【兼容入口】rollback-* | tag={} | 内部按 T{} 执行真实下单实验", tag, version);
        Map<String, Object> run = orderTxExperimentService.run(null, 1L, 1, version);
        boolean rolledBack = Boolean.TRUE.equals(run.get("rolledBack"));

        Map<String, Object> data = new HashMap<>(8);
        data.put("scenario", run.get("scenario"));
        data.put("expect", rolledBack
                ? "回滚成功，业务表（orders/product/shopping_cart）不留任何痕迹"
                : "事务不回滚，业务表会留下脏数据（订单/库存/购物车）");
        data.put("remaining", rolledBack ? 0 : 1);
        data.put("result", run.get("verdict"));
        data.put("tag", tag);
        data.put("detail", run);
        return data;
    }

    /**
     * 清理事务实验数据（orders / order_item / shopping_cart），演示完收尾用。
     *
     * <p>⚠️ data 是<b>清理的总行数</b>（数字）—— 前端演示台会直接把它拼进提示文案，
     * 不要改成返回对象。</p>
     */
    @PostMapping("/tx/clean")
    public Result<Integer> txClean(@RequestParam(required = false) Long userId) {
        Map<String, Object> data = orderTxExperimentService.clean(userId);
        int total = ((Number) data.getOrDefault("orderRows", 0)).intValue()
                + ((Number) data.getOrDefault("itemRows", 0)).intValue()
                + ((Number) data.getOrDefault("cartRows", 0)).intValue();
        log.info("【课堂实验】清理事务实验数据 | userId={} | {}", userId, data);
        return Result.success("已清理", total);
    }

    /* ==================================================================
     * 三、并发超卖实验（第 13~16 课时）
     * ------------------------------------------------------------------
     * 前端演示台（mall-web /admin/demo 的"并发超卖"页签）依赖的一组接口。
     * 压测由后端线程池 + CountDownLatch 真并发发起（见 OversellPressureService），
     * 不再依赖 Node 网关；版本切换走运行期覆盖，不用改 yml 重启。
     * ================================================================== */

    /**
     * 查看当前下单版本（V1~V5）：运行期覆盖值优先，其次 yml
     */
    @GetMapping("/order/version")
    public Result<Map<String, Object>> orderVersion() {
        return Result.success(orderOversellService.status());
    }

    /**
     * 热切换下单版本：value = 1~5（V1 无锁 / V2 事务内锁 / V3 单机锁 /
     * V4 Redis 锁 / V5 原子扣减） / 0（清除覆盖，回到 yml 配置）。
     * <p>课堂上切完版本立刻压下一轮，超卖 → 修复全程不用重启。</p>
     */
    @PostMapping("/order/version")
    public Result<Map<String, Object>> setOrderVersion(@RequestParam(defaultValue = "0") Integer value) {
        return Result.success(orderOversellService.setVersion(value));
    }

    /** 查看商品库存概况（含 oversell 判定，前端库存看板直接渲染） */
    @GetMapping("/stock")
    public Result<Map<String, Object>> stock(@RequestParam(defaultValue = "1") Long productId) {
        return Result.success(oversellDataService.stockInfo(productId));
    }

    /** 重置库存（销量一并清零）：每一轮压测前恢复现场用 */
    @PostMapping("/stock/reset")
    public Result<Map<String, Object>> resetStock(@RequestParam(defaultValue = "1") Long productId,
                                                  @RequestParam(defaultValue = "10") Integer stock) {
        return Result.success("库存已重置", oversellDataService.resetStock(productId, stock));
    }

    /**
     * 清理压测订单（只删 remark = loadtest 的订单，不碰真实订单）。
     * <p>⚠️ data 是 {orderRows, itemRows}，前端会拆开拼提示文案。</p>
     */
    @PostMapping("/orders/clean")
    public Result<Map<String, Object>> cleanOrders() {
        return Result.success("已清理", oversellDataService.cleanOrders());
    }

    /**
     * 并发压测：重置库存 → 清理订单 → N 个线程同时下单 → 读回库存判定。
     * <p>替代老版 Node 网关的 /bff/pressure-test，浏览器并发上限是 6 压不出效果，
     * 服务端线程池才是真并发（压测代码本身也是第 13 课时的并发教材）。</p>
     */
    @PostMapping("/pressure/run")
    public Result<Map<String, Object>> pressureRun(@RequestBody OversellPressureDTO dto) {
        return Result.success("压测完成", oversellPressureService.run(dto));
    }

    /* ==================================================================
     * 四、秒杀异步下单实验（第 20 课时 · RabbitMQ）
     * ------------------------------------------------------------------
     * 前端 MQ 实验台（mall-web /admin/mq）依赖的一组接口。
     * 和上面"并发超卖"那一组的区别：
     *   · 那里压的是 mall.order.version（1~5），链路是"接口里直接扣库存"；
     *   · 这里压的是 mall.mq.mode（6~8），链路是"Redis 预扣 → MQ 排队 → 消费者异步落库"。
     * 两套开关互不干扰，可以同时存在。
     * ================================================================== */

    /**
     * MQ 运行状态看板：连接 / 拓扑 / 队列积压 / 在线消费者 / 生产消费计数器 / 当前版本。
     * <p>课堂上压完一轮刷新它，能看到队列积压从几十掉到 0 —— 这就是削峰填谷。</p>
     */
    @GetMapping("/mq/status")
    public Result<Map<String, Object>> mqStatus() {
        return Result.success(mqStatusService.status());
    }

    /**
     * 热切换消费端版本：value = 6（只有 MQ，会超卖） / 7（幂等 + 原子扣减） /
     * 8（最终版：Redis Lua 预扣 + MQ + DB 兜底） / 0（清除覆盖，回到 yml）。
     *
     * <p>注意这里既影响<b>消费端</b>（怎么落库），也影响<b>入口</b>
     * （V8 才会先做 Redis 预扣）—— 因为 V7 和 V8 的差别本来就只在入口。
     * 切完不用重启，下一轮洪峰实验立刻按新版本跑。</p>
     */
    @PostMapping("/mq/mode")
    public Result<Map<String, Object>> mqMode(@RequestParam(defaultValue = "0") Integer value) {
        return Result.success(mqModeService.set(value));
    }

    /**
     * 手工投递一条消息（不用登录，方便课堂上只看"一条"的完整链路）。
     *
     * <p>想观察 <b>V7/V8 的幂等拦截</b>怎么做？不用改代码：
     * 去 RabbitMQ 管理台（{@code http://localhost:15672} → Queues →
     * {@code mall.order.queue} → Get messages）把同一条消息重新投一次，
     * 就能看到"幂等拦截 +1"。</p>
     */
    @PostMapping("/mq/submit")
    public Result<Map<String, Object>> mqSubmit(@RequestParam(defaultValue = "1") Long productId,
                                                @RequestParam(defaultValue = "1") Integer quantity,
                                                @RequestParam(defaultValue = "2001") Long userId) {
        Map<String, Object> data = orderMqService.submit(userId, productId, quantity,
                "MQ 实验手工下单", "13800000000", "实验地址（手工投递）",
                OversellSupport.LOADTEST_REMARK);
        return Result.success("已投递一条消息", data);
    }

    /**
     * 一键洪峰：重置库存 → 并发投递 N 条消息 → 等消费完 → 判定有没有超卖。
     * <p>返回体里带了 {@code steps}（实验步骤）和 {@code logs}（消费流水），
     * 前端实验台直接展开给学生看。</p>
     */
    @PostMapping("/mq/flood")
    public Result<Map<String, Object>> mqFlood(@RequestParam(required = false) Long productId,
                                               @RequestParam(defaultValue = "10") Integer stock,
                                               @RequestParam(defaultValue = "20") Integer count) {
        return Result.success("洪峰实验完成", mqExperimentService.flood(productId, stock, count));
    }

    /** 清空业务队列 + 死信队列，并把计数器、结果缓存归零（每轮实验前恢复现场） */
    @PostMapping("/mq/purge")
    public Result<Map<String, Object>> mqPurge() {
        return Result.success("已清空", mqStatusService.purge());
    }

    /**
     * 把数据库库存预热进 Redis（V8 的入口门禁依赖它）。
     *
     * <p>为什么需要显式预热？因为 Redis 里的库存是 DB 库存的<b>副本</b>。
     * 副本不存在时，V8 的 Lua 脚本会返回 -2（未预热）并<b>拒绝下单</b> ——
     * 这是故意的：缓存数据没准备好属于故障，绝不能降级成"卖完了"，
     * 否则 Redis 数据丢了一整天都没人知道。</p>
     */
    @PostMapping("/stock/warmup")
    public Result<Map<String, Object>> stockWarmup(@RequestParam(defaultValue = "1") Long productId,
                                                   @RequestParam(required = false) Integer stock) {
        Map<String, Object> info = oversellDataService.stockInfo(productId);
        int target = stock == null ? ((Number) info.get("stock")).intValue() : stock;
        stockRedisService.warmup(productId, target);

        Map<String, Object> data = new HashMap<>(8);
        data.putAll(info);
        data.put("redisKey", stockRedisService.stockKey(productId));
        data.put("redisStock", stockRedisService.getStock(productId));
        return Result.success("已预热 Redis 库存：" + stockRedisService.stockKey(productId) + " = " + target, data);
    }

    /** 通过注入的 Bean 调用 → 异步生效，日志线程名为 mall-async-x */
    @GetMapping("/async/ok")
    public Result<String> asyncOk(@RequestParam String orderNo) {
        orderAsyncService.asyncRecordLog(orderNo, "通过注入的 Bean 调用，@Async 应当生效");
        return Result.success("已提交异步任务，请看控制台线程名（应为 mall-async-*）");
    }

    /** 同类 this 调用 → 异步失效，日志线程名为 http-nio-8080-exec-x */
    @GetMapping("/async/fail")
    public Result<String> asyncFail(@RequestParam String orderNo) {
        orderAsyncService.selfInvocationDemo(orderNo);
        return Result.success("已执行，请看控制台线程名（会是 http-nio-*，说明异步失效）");
    }
}
