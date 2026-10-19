package com.example.mall.controller;

import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import com.example.mall.config.MallProperties;
import com.example.mall.exception.BusinessException;
import com.example.mall.order.tx.OrderTxExperimentService;
import com.example.mall.order.tx.OrderTxSubmitService;
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

}