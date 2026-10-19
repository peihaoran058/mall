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
 * 【T5 · BUG】@Transactional 写在非 public 方法上 —— 被"静默忽略"
 * ============================================================================
 *
 * <p>这个坑最气人的地方是<b>连个警告都没有</b>：不报错、不提示、
 * IDEA 不给黄线，代码看起来和正确写法一模一样，只有查数据时才发现事务没生效。</p>
 *
 * <pre>
 * @Transactional(rollbackFor = Exception.class)
 * private Orders privateCreateOrder(...) {   // ❌ private
 *     ...
 * }
 * </pre>
 *
 * <p><b>原因：</b>{@code @Transactional} 靠动态代理实现，</p>
 * <ul>
 *     <li><b>JDK 动态代理</b>：只能代理<b>接口里的 public 方法</b>；</li>
 *     <li><b>CGLIB</b>：靠继承 + 覆写实现，能覆写 public / protected，
 *         但<b>覆写不了 private 方法</b>，也无法拦截 static / final 方法。</li>
 * </ul>
 * <p>所以注解加在 private 上就是"写了但没人执行"，事务根本不存在。</p>
 *
 * <blockquote>
 * <b>顺便把三个"静默失效"凑齐（学员复习用）：</b>
 * <br>① 非 public 方法（本类）
 * <br>② 同类自调用（{@link OrderTxV4Service}）
 * <br>③ {@code final} / {@code static} 方法上加事务注解（CGLIB 覆写不了）
 * </blockquote>
 *
 * <p><b>期望结果：</b>表现与 T4 相同（订单残留、库存已扣、购物车已清），
 * 但<b>成因不同</b> —— T4 是"注解写对了但调用方式错了"，
 * T5 是"注解从一开始就是无效的"。课堂上要把这两句分开说清楚。</p>
 *
 * <p><b>修法：</b>把事务方法暴露成 {@code public}；如果确实要限制调用范围，
 * 用包级可见也不行（默认包级也不是 public，JDK 代理同样拦不到），
 * 老老实实 public + 拆类。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxV5Service {

    private final OrderService support;

    /** 外层方法：无事务（也只能无事务——它调不动被代理的方法） */
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        log.info("【T5】外层方法执行完毕，即将调用 private + @Transactional 的内层方法");
        return this.privateCreateOrder(userId, dto);   // ❌ 注解被静默忽略
    }

    /**
     * 注解写得"很正确"，但它是 private —— CGLIB 覆写不了，
     * <b>Spring 不会报错，也不会生效</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    private Orders privateCreateOrder(Long userId, OrderSubmitDTO dto) {
        Orders order = support.submit(userId, dto);
        log.info("【T5】落库完成（无事务，已自动提交），orderNo={}，接下来抛异常", order.getOrderNo());
        throw new RuntimeException("[模拟] 下单成功后扣积分失败");
    }
}
