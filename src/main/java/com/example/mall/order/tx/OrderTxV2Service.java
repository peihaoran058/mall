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
 * 【T2 · BUG】漏写 rollbackFor —— 抛受检异常，事务照常提交，脏数据入库
 * ============================================================================
 *
 * <p><b>这是"最容易被忽略"的一种事务失效</b>，因为它没有任何报错：
 * 代码编译通过、方法抛了异常、日志也有，但数据库<b>已经脏了</b>。</p>
 *
 * <p>代码与 {@link OrderTxV1Service} 逐行相同，<b>只少了 {@code rollbackFor}</b>：</p>
 *
 * <pre>
 * @Transactional                                  // ❌ 默认只回滚 RuntimeException / Error
 * public Orders submit(...) throws IOException {
 *     Orders order = support.doCreateOrder(...);  // 这三步都会真的落库
 *     callRiskApi(order.getOrderNo());            // 抛 IOException（受检异常）
 *     return order;
 * }
 * </pre>
 *
 * <p><b>为什么受检异常不回滚？</b>
 * Spring 的默认回滚规则是 {@code rollbackFor = {RuntimeException.class, Error.class}}。
 * 这是<b>有意的设计</b>：框架认为受检异常是"调用方预期内的、要处理的"，
 * 而运行时异常才是"系统出问题了"。所以 {@code IOException} 抛出去，
 * 事务拦截器判定"不需要回滚"，直接 commit 然后才把异常往上抛。</p>
 *
 * <p><b>脏数据的三种表现（课堂上一一对应去查库）：</b></p>
 * <ol>
 *     <li>接口返回"下单失败"，但 {@code orders} 里有这条订单 —— 用户看到失败却生成了订单</li>
 *     <li>{@code product.stock} 已经扣掉，{@code shopping_cart} 也被清空 —— 用户没下单成功却丢了库存</li>
 *     <li>{@code order_log} 里<b>没有</b>这条订单的日志（因为异常在写日志之前）—— 排查时"查无此单"</li>
 * </ol>
 *
 * <p><b>修法（二选一）：</b>① 补上 {@code rollbackFor = Exception.class}（见 T1）；
 * ② 把受检异常包成运行时异常再抛 —— 本项目自定义的 {@code BusinessException}
 * 就是继承 {@code RuntimeException} 的，业务异常天然会被回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxV2Service {

    private final OrderService support;

    /** ❌ 与 T1 唯一的差别：少了 rollbackFor = Exception.class */
    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, OrderSubmitDTO dto) throws IOException {
        Orders order = support.submit(userId, dto);
        log.info("【T2】落库完成，orderNo={}，接下来调用外部风控接口（这一步会抛受检异常）", order.getOrderNo());

        callRiskApi(order.getOrderNo());   // ← 受检异常，事务不会回滚

        log.info("【T2】永远不会执行到这里");
        return order;
    }

    /** 同 T1：模拟外部风控接口抛受检异常 */
    private void callRiskApi(String orderNo) throws IOException {
        throw new IOException("[模拟] 调用外部风控接口超时：orderNo=" + orderNo);
    }
}
