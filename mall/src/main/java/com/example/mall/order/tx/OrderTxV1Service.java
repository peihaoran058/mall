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
 * 【T1】正确写法 —— 事务包住"落库 + 外部调用"，异常时整单回滚
 * ============================================================================
 *
 * <p><b>这一版不制造 BUG，它是其余四版的参照物。</b>
 * 它和 {@link OrderTxV2Service} 是<b>一模一样的代码</b>，
 * 只差一个 {@code rollbackFor} —— 请把两个类并排投影给学员看。</p>
 *
 * <p>和真实下单链路（{@code OrderVersionV3Service#submit}）唯一的差别是：
 * 真实链路里事务范围<b>只包住落库</b>，外部调用放在事务外面；
 * 这里为了让"回滚"看得见，故意把"调外部风控接口"也放进事务里，让它抛异常。</p>
 *
 * <pre>
 * @Transactional(rollbackFor = Exception.class)   // ← T2 少了这个属性
 * public Orders submit(...) throws IOException {
 *     Orders order = support.doCreateOrder(...);  // 扣库存 + 建订单 + 清购物车
 *     callRiskApi(order.getOrderNo());            // ← 抛受检异常 IOException
 *     return order;
 * }
 * </pre>
 *
 * <p><b>期望结果（数据库里应该什么都看不到）：</b></p>
 * <ul>
 *     <li>{@code product.stock} 回到下单前的值（扣减被回滚）</li>
 *     <li>{@code orders} / {@code order_item} 里没有这条订单</li>
 *     <li>{@code shopping_cart} 里那条勾选记录还在（没被清掉）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxV1Service {

    private final OrderService support;

    /** ★ 与 T2 唯一的差别：多了 rollbackFor = Exception.class */
    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, OrderSubmitDTO dto) throws IOException {
        Orders order = support.submit(userId, dto);
        log.info("【T1】落库完成，orderNo={}，接下来调用外部风控接口（这一步会抛异常）", order.getOrderNo());
        callRiskApi(order.getOrderNo());   // ← 抛受检异常

        log.info("【T1】永远不会执行到这里");
        return order;
    }

    /** 模拟"下单成功后要调外部风控/支付预下单接口"——真实项目里这类调用抛的就是受检异常 */
    private void callRiskApi(String orderNo) throws IOException {
        throw new IOException("[模拟] 调用外部风控接口超时：orderNo=" + orderNo);
    }


    @Transactional
    public void inner()  {
       /////
    }
}
