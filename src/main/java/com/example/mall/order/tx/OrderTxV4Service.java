package com.example.mall.order.tx;

import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * 【T4 · BUG】同类内部自调用 —— @Transactional 被完全绕过，等于没有事务
 * ============================================================================
 *
 * <p>这个坑在本项目里<b>就有一个真实的正解</b>：
 * {@code OrderVersionV3Service#submit} 在外层加锁、通过<b>注入的</b>
 * {@code orderCoreService} 调用内层事务方法。当时讲的是"锁要包住事务"，
 * 但它能成立的真正前提就是<b>跨 Bean 调用</b>；一旦改成 {@code this.xxx()}，
 * 事务直接消失 —— 这才是 T4 要讲的点。</p>
 *
 * <pre>
 * public Orders submit(...) {                    // 外层：没有 @Transactional
 *     prepare(dto);
 *     return this.innerCreateOrder(userId, dto); // ❌ this 调用，走的是原始对象
 * }
 *
 * @Transactional(rollbackFor = Exception.class)
 * public Orders innerCreateOrder(...) {
 *     Orders order = support.doCreateOrder(...);
 *     throw new RuntimeException("...");         // 期望回滚，实际不会
 * }
 * </pre>
 *
 * <p><b>为什么？</b>Spring 的 {@code @Transactional} 是靠<b>动态代理</b>实现的。
 * 外面注入进来的是<b>代理对象</b>，代理在方法调用前后插入"开启/提交/回滚事务"。
 * 而类内部的 {@code this} 指向的是<b>原始对象</b>，压根不经过代理，
 * 所以内层方法上的注解形同虚设 —— <b>整个调用链上没有任何事务</b>，
 * 每次 insert/update 都是自动提交。</p>
 *
 * <pre>
 * 正常调用：调用方 ──► [代理对象] ──► 目标对象      ← 事务在这里生效
 * 自用调用：目标对象 ──this.inner()──► 目标对象    ← 代理被绕过 ✗
 * </pre>
 *
 * <p><b>期望结果：</b>接口抛"扣积分失败"，但 {@code orders} 里有订单、
 * {@code product.stock} 已扣、{@code shopping_cart} 已清 —— 全都没回滚。</p>
 *
 * <p><b>三种正确写法：</b></p>
 * <ol>
 *     <li><b>拆成两个 Bean（本项目采用）</b>：内层挪到 {@code OrderCoreService}，
 *         外层注入后调用 —— 见 {@code OrderVersionV1Service#submit}；</li>
 *     <li>自己注入自己：{@code @Autowired private OrderTxV4Service self;} 然后 {@code self.innerCreateOrder(...)}；</li>
 *     <li>用 {@code AopContext.currentProxy()}（需开启 {@code exposeProxy = true}）。</li>
 * </ol>
 *
 * <p><b>三胞胎提醒：</b>{@code @Transactional}、{@code @Async}、{@code @Cacheable}
 * 失效的原因<b>完全一样</b> —— 动态代理被绕过（对比
 * {@code OrderAsyncService#selfInvocationDemo}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxV4Service {

    private final OrderService support;

    /** 外层方法<b>故意不加</b>事务注解，模拟真实链路里的"参数校验/组装层" */
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        log.info("【T4】外层方法（无事务）执行完毕，即将用 this 调用内层带 @Transactional 的方法");
        return this.innerCreateOrder(userId, dto);   // ❌ 自调用：代理被绕过
    }

    /**
     * 内层方法：注解写得完全正确，但因为是被 {@code this} 调用的，<b>不会生效</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    public Orders innerCreateOrder(Long userId, OrderSubmitDTO dto) {
        Orders order = support.submit(userId, dto);
        log.info("【T4】落库完成（无事务，已自动提交），orderNo={}，接下来抛异常", order.getOrderNo());
        throw new RuntimeException("[模拟] 下单成功后扣积分失败");
    }
}
