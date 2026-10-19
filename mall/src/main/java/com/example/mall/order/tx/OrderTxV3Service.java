package com.example.mall.order.tx;

import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * ============================================================================
 * 【T3 · BUG】异常被自己 try-catch 吃掉了 —— 事务管理器根本不知道出过错
 * ============================================================================
 *
 * <p><b>这是线上最常见的一种事务失效。</b>因为开发同学往往觉得
 * "业务异常我 catch 掉、打个日志比较优雅，不能让用户看到报错"，
 * 结果悄悄埋了雷 —— 而且是加了 {@code rollbackFor} 也救不回来的那种。</p>
 *
 * <pre>
 * @Transactional(rollbackFor = Exception.class)   // ← 注解写对了，但没救
 * public Orders submit(...) {
 *     Orders order = support.doCreateOrder(...);
 *     try {
 *         callRiskApi(order.getOrderNo());
 *     } catch (IOException e) {
 *         log.warn("风控接口异常，已忽略", e);      // ❌ 异常到此为止，没有重新抛出
 *     }
 *     return order;                               // 方法"正常返回"
 * }
 * </pre>
 *
 * <p><b>原理（这一句必须让学员记住）：</b><br>
 * 事务的回滚<b>不是</b>靠"代码里有没有抛过异常"判断的，而是靠
 * <b>"异常有没有抛到 Spring 的事务代理那一层"</b>判断的。
 * 你把异常吃在方法内部，代理看到的是"方法正常返回"，于是高高兴兴地提交了事务。</p>
 *
 * <p><b>期望结果：</b>与 T2 完全一样 —— 接口返回"下单成功"，
 * 但 {@code product.stock} 已扣、{@code shopping_cart} 已清。
 * （本实验里 T3 刻意<b>不</b>往外抛异常，所以调用方拿到的是"成功"，更隐蔽。）</p>
 *
 * <p><b>修法（两种，看业务语义选）：</b></p>
 * <pre>
 * // ✅ 方案一：catch 后重新抛（推荐，语义最清楚）
 * catch (IOException e) {
 *     log.error("风控接口异常", e);
 *     throw new BusinessException("下单失败：风控校验未通过");
 * }
 *
 * // ✅ 方案二：确实不想抛（比如"记录日志失败不该影响下单"），那就主动标记回滚
 * catch (IOException e) {
 *     log.error("风控接口异常", e);
 *     TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
 * }
 * </pre>
 *
 * <p>⚠️ 方案二要注意：标记之后即使方法正常返回，事务最终也会回滚，
 * 但会抛 {@code UnexpectedRollbackException}，调用方需要处理。
 * 另外，如果这个 catch 只想忽略"不影响主流程"的次要动作（比如写操作日志），
 * 那<b>不回滚才是对的</b> —— 关键是<b>想清楚</b>，而不是哪一边更省事。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxV3Service {

    private final OrderService support;

    /**
     * ❌ 注解是对的，错在把异常吃掉了。
     * <p>不抛异常 → 调用方会收到"下单成功"，而库里其实已经扣了库存。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        Orders order = support.submit(userId, dto);

        try {
            callRiskApi(order.getOrderNo());
        } catch (IOException e) {
            // ❌ 只记日志，没有重新抛出 —— 事务代理认为方法正常返回，直接 commit
            log.warn("【T3】风控接口异常，已忽略 | orderNo={} | msg={}", order.getOrderNo(), e.getMessage());
            throw new RuntimeException("【T3】风控接口异常，请稍后再试");
        }

        log.info("【T3】方法正常返回，事务即将提交（前面的扣减和建单都会被保留）");
        return order;
    }

    /** 同 T1/T2：模拟外部风控接口抛受检异常 */
    private void callRiskApi(String orderNo) throws IOException {
        throw new IOException("[模拟] 调用外部风控接口超时：orderNo=" + orderNo);
    }
}
