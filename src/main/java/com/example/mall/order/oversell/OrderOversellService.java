package com.example.mall.order.oversell;

import com.example.mall.config.MallProperties;
import com.example.mall.entity.Orders;
import com.example.mall.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 * 并发超卖实验的"分发开关"（第 13~16 课时）
 * ============================================================================
 *
 * <p>和 {@code OrderTxSubmitService}（事务失效专题，tx-version 1~5）
 * 是同一个套路的两个开关，互不干扰：</p>
 *
 * <pre>
 * mall.order.version      1~5   并发超卖专题（第 13~16 课时）★ 本类
 * mall.order.tx-version   1~5   事务失效专题（第 10~11 课时）
 * </pre>
 *
 * <table border="1">
 *     <caption>版本对照（与前端演示台的对照表一一对应）</caption>
 *     <tr><th>版本</th><th>方案</th><th>压测预期</th></tr>
 *     <tr><td>1</td><td>无锁：读 → Java 判断 → 无条件扣减</td><td>❌ 库存负数（超卖）</td></tr>
 *     <tr><td>2</td><td>@Transactional + synchronized 同方法</td><td>❌ 锁包不住事务，仍超卖</td></tr>
 *     <tr><td>3</td><td>synchronized 移到事务外层</td><td>✅ 单机正确；多实例失效</td></tr>
 *     <tr><td>4</td><td>Redis 分布式锁</td><td>✅ 集群正确</td></tr>
 *     <tr><td>5</td><td>UPDATE ... WHERE stock &gt;= n</td><td>✅ 最优解，推荐</td></tr>
 * </table>
 *
 * <p>课堂上不用改 yml 重启：前端演示台直接调
 * {@code POST /api/demo/order/version?value=2} 热切换，
 * 切完立刻压下一轮，超卖 → 修复的过程一气呵成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOversellService {

    private final MallProperties mallProperties;
    private final OrderOversellV1Service v1Service;
    private final OrderOversellV2Service v2Service;
    private final OrderOversellV3Service v3Service;
    private final OrderOversellV4Service v4Service;
    private final OrderOversellV5Service v5Service;

    /**
     * 运行期覆盖值（课堂热切换用，不用重启）。
     * <p>{@code null} = 跟随 yml 里的 {@code mall.order.version}。
     * 用 {@code volatile} 保证一个请求线程改了，压测线程立刻能看到。</p>
     */
    private volatile Integer runtimeVersion = null;

    /** 按当前生效版本下单（yml 开关 或 运行期覆盖值） */
    public Orders submit(Long userId, Long productId) {
        int version = currentVersion();
        log.info(">>> 超卖实验 | version={} | {}", version, describe(version));
        switch (version) {
            case 1:
                return v1Service.submit(userId, productId);
            case 2:
                return v2Service.submit(userId, productId);
            case 3:
                return v3Service.submit(userId, productId);
            case 4:
                return v4Service.submit(userId, productId);
            case 5:
                return v5Service.submit(userId, productId);
            default:
                throw new BusinessException("未知的下单版本：" + version + "（只支持 1~5）");
        }
    }

    /** 当前生效版本：优先运行期覆盖，其次 yml（yml 没配默认 V1） */
    public int currentVersion() {
        Integer override = this.runtimeVersion;
        if (override != null) {
            return override;
        }
        Integer configured = mallProperties.getOrder().getVersion();
        return configured == null ? 1 : configured;
    }

    /**
     * 运行期切换版本：value = 1~5（实验版本） / 0（清除覆盖，回到 yml 配置）。
     */
    public Map<String, Object> setVersion(Integer value) {
        int v = value == null ? 0 : value;
        if (v < 0 || v > 5) {
            throw new BusinessException("value 只能是 0（清除覆盖）或 1~5（实验版本）");
        }
        this.runtimeVersion = v == 0 ? null : v;

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("runtimeOverride", this.runtimeVersion);
        data.put("ymlConfigured", mallProperties.getOrder().getVersion());
        data.put("effectiveVersion", currentVersion());
        data.put("raceWindowMillis", mallProperties.getOrder().getRaceWindowMillis());
        data.put("description", describe(currentVersion()));
        data.put("message", v == 0
                ? "已清除运行期覆盖，回到 application.yml 的配置"
                : "已切换到 V" + v + "，下一次压测就会按这一版执行");
        log.info("【课堂实验】切换下单版本 → V{} | {}", currentVersion(), describe(currentVersion()));
        return data;
    }

    /** 当前状态，给前端演示台的状态卡用 */
    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("version", currentVersion());
        data.put("runtimeOverride", this.runtimeVersion);
        data.put("ymlConfigured", mallProperties.getOrder().getVersion());
        data.put("raceWindowMillis", mallProperties.getOrder().getRaceWindowMillis());
        data.put("description", describe(currentVersion()));
        return data;
    }

    /** 版本说明，给接口返回和日志用 */
    public String describe(int version) {
        switch (version) {
            case 1:
                return "V1 无锁：读库存 → Java 判断 → 直接扣减，三步之间没有原子性（超卖的根源）";
            case 2:
                return "V2 BUG：synchronized 加在 @Transactional 方法上，锁先释放、事务后提交，包不住";
            case 3:
                return "V3 单机锁：锁移到事务外层，单机正确；多实例部署 synchronized 失效";
            case 4:
                return "V4 Redis 分布式锁：SET NX EX + UUID 标识 + Lua 原子释放，集群安全";
            case 5:
                return "V5 原子扣减：UPDATE ... WHERE stock >= n，数据库行锁保证原子性（最推荐）";
            default:
                return "未知版本";
        }
    }
}
