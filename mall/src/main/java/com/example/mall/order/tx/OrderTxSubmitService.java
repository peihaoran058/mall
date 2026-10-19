package com.example.mall.order.tx;

import com.example.mall.config.MallProperties;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 * 事务失效实验的"分发开关"（第 11 课时）
 * ============================================================================
 *
 * <p>和 {@code OrderSubmitService}（按 {@code mall.order.version} 分发 V1~V5）
 * 是同一个套路的两个开关，互不干扰：</p>
 *
 * <pre>
 * mall.order.version     1~5   并发超卖专题（第 13~16 课时）
 * mall.order.tx-version  1~5   事务失效专题（第 10~11 课时）★ 本类
 * mall.mq.mode           6~7   秒杀异步下单（第 20 课时）
 * </pre>
 *
 * <p><b>和旧版 demo 实验最大的区别：</b>这五个版本<b>都是真实下单</b> ——
 * 会真的扣 {@code product.stock}、真的往 {@code orders}/{@code order_item} 插单、
 * 真的清 {@code shopping_cart}。所以验证方式是<b>查业务表</b>，
 * 不再往 {@code order_log} 插假数据（order_no = TX-DEMO）来凑数。</p>
 *
 * <table border="1">
 *     <caption>版本对照</caption>
 *     <tr><th>版本</th><th>场景</th><th>接口表现</th><th>数据库结果</th></tr>
 *     <tr><td>1</td><td>正确：跨 Bean 事务 + rollbackFor</td><td>报错</td><td>✅ 全部回滚</td></tr>
 *     <tr><td>2</td><td>漏写 rollbackFor + 受检异常</td><td>报错</td><td>❌ 订单残留、库存已扣</td></tr>
 *     <tr><td>3</td><td>异常被 try-catch 吃掉</td><td>"成功"</td><td>❌ 订单残留、库存已扣</td></tr>
 *     <tr><td>4</td><td>同类自调用</td><td>报错</td><td>❌ 订单残留、库存已扣</td></tr>
 *     <tr><td>5</td><td>@Transactional 写在 private 上</td><td>报错</td><td>❌ 订单残留、库存已扣</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxSubmitService {

    private final MallProperties mallProperties;
    private final OrderTxV1Service v1Service;
    private final OrderTxV2Service v2Service;
    private final OrderTxV3Service v3Service;
    private final OrderTxV4Service v4Service;
    private final OrderTxV5Service v5Service;

    /**
     * 运行期覆盖值（课堂热切换用，不用重启）。
     * <p>{@code null} = 跟随 yml 里的 {@code mall.order.tx-version}。
     * 用 {@code volatile} 保证一个请求线程改了，其他线程立刻能看到。</p>
     */
    private volatile Integer runtimeVersion = null;

    /** 按当前生效的版本下单（yml 开关 或 运行期覆盖值） */
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        return submit(userId, dto, currentVersion());
    }

    /** 按指定版本下单 */
    public Orders submit(Long userId, OrderSubmitDTO dto, int version) {
        log.info(">>> 事务失效实验 | tx-version={} | {}", version, describe(version));
        try {
            switch (version) {
                case 1:
                    return v1Service.submit(userId, dto);
                case 2:
                    return v2Service.submit(userId, dto);
                case 3:
                    return v3Service.submit(userId, dto);
                case 4:
                    return v4Service.submit(userId, dto);
                case 5:
                    return v5Service.submit(userId, dto);
                default:
                    throw new BusinessException("未知的事务实验版本：" + version);
            }
        } catch (IOException e) {
            // T1/T2 两版抛的是受检异常。这里统一转成运行时异常往上抛，
            // ⚠️ 注意转换发生的时机：Spring 早就做完"提交还是回滚"的决定了，
            //    所以在 T2 里数据已经提交，转异常不会把它变回滚。
            throw new BusinessException("下单失败（外部风控接口异常）：" + e.getMessage());
        }
    }

    /** 当前生效版本：优先运行期覆盖，其次 yml */
    public int currentVersion() {
        Integer override = this.runtimeVersion;
        if (override != null) {
            return override;
        }
        Integer configured = mallProperties.getOrder().getTxVersion();
        return configured == null ? 0 : configured;
    }

    /**
     * 运行期切换版本：value = 1~5（实验版本） / 0（清除覆盖，回到 yml 配置）。
     * <p>课堂上不用反复重启：先跑 2 看到脏数据，再切 1 看回滚，
     * 前后对比非常直观。</p>
     */
    public Map<String, Object> setMode(Integer value) {
        int v = value == null ? 0 : value;
        if (v < 0 || v > 5) {
            throw new BusinessException("value 只能是 0（清除覆盖）或 1~5（实验版本）");
        }
        this.runtimeVersion = v == 0 ? null : v;

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("runtimeOverride", this.runtimeVersion);
        data.put("ymlConfigured", mallProperties.getOrder().getTxVersion());
        data.put("effectiveVersion", currentVersion());
        data.put("description", describe(currentVersion()));
        data.put("message", v == 0
                ? "已清除运行期覆盖，回到 application.yml 的配置"
                : "已切换到 tx-version=" + v + "，下一次下单就会按这一版执行");
        log.info("【课堂实验】切换事务实验版本 → {} | {}", currentVersion(), describe(currentVersion()));
        return data;
    }

    /** 版本说明，给接口返回和日志用 */
    public String describe(int version) {
        switch (version) {
            case 1:
                return "T1 正确：@Transactional(rollbackFor) 包住'落库 + 外部调用' → 异常整体回滚";
            case 2:
                return "T2 BUG：漏写 rollbackFor + 抛受检异常 → 不回滚，脏数据入库";
            case 3:
                return "T3 BUG：异常被 try-catch 吃掉 → 注解写对了也没用，脏数据入库";
            case 4:
                return "T4 BUG：同类自调用 → 事务被完全绕过（就是没事务）";
            case 5:
                return "T5 BUG：@Transactional 写在 private 方法上 → 被静默忽略";
            case 0:
            default:
                return "未启用：下单走正常链路（mall.order.version 决定的 V1~V5）";
        }
    }
}
