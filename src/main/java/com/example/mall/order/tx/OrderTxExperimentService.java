package com.example.mall.order.tx;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.ShoppingCartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * 事务失效实验的编排（第 11 课时）—— "造数据 → 下单 → 判定" 一把梭
 * ============================================================================
 *
 * <p>课堂上最怕的是"为了演示一个点，要先手动做五步准备"。这个类把那几步收进来：</p>
 * <ol>
 *     <li>清场：删掉该实验用户的购物车与历史订单，保证每次实验起点一致</li>
 *     <li>重置库存：把商品库存设成 {@code quantity * 2}，保证够下单</li>
 *     <li>造购物车：给实验用户加一条<b>勾选</b>的购物车记录（下单必须有购物车数据）</li>
 *     <li>记录下单前快照：订单数 / 库存 / 购物车条数</li>
 *     <li>调 {@link OrderTxSubmitService} 真实下单，捕获异常</li>
 *     <li>记录下单后快照，给出"到底回滚了没有"的结论</li>
 * </ol>
 *
 * <p><b>⚠️ 这里操作的全是业务表</b>（shopping_cart / product / orders / order_item），
 * 不是往日志表插假数据。所以实验跑完，学员可以直接用 SQL 自己复核结论。</p>
 *
 * <p><b>判定标准：</b>这五版的<b>业务预期都是"回滚"</b>（下单没成功，就不该留下任何痕迹），
 * 只有 T1 做到了。所以 {@code rolledBack = true} 才算对。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTxExperimentService {

    /** 课堂实验专用账号，避免污染真实用户数据 */
    public static final Long DEFAULT_USER_ID = 2001L;

    private final ShoppingCartMapper shoppingCartMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final OrderTxSubmitService orderTxSubmitService;

    /**
     * 跑一次实验。
     *
     * @param userId    实验用户（默认 {@value #DEFAULT_USER_ID}）
     * @param productId 商品 id，课堂默认 1
     * @param quantity  购买数量
     * @param version   实验版本 1~5；传 null 表示用当前生效版本
     */
    public Map<String, Object> run(Long userId, Long productId, Integer quantity, Integer version) {
        long uid = userId == null ? DEFAULT_USER_ID : userId;
        long pid = productId == null ? 1L : productId;
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;
        int ver = version == null ? orderTxSubmitService.currentVersion() : version;
        if (ver < 1 || ver > 5) {
            throw new BusinessException("version 只能是 1~5（1 正确版 / 2~5 四种失效版）");
        }

        // ---------- 1~3. 清场并构造一个"刚好能下单"的现场 ----------
        resetScene(uid, pid, qty);

        long ordersBefore = countOrders(uid);
        int stockBefore = currentStock(pid);
        long cartBefore = countCart(uid);

        // ---------- 4. 真实下单 ----------
        String apiResult;
        String apiError = null;
        try {
            Orders order = orderTxSubmitService.submit(uid, buildDto(), ver);
            apiResult = "接口正常返回（下单成功） | orderNo=" + order.getOrderNo();
        } catch (Exception e) {
            apiResult = "接口抛异常（下单失败）";
            apiError = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.info("【实验】tx-version={} 下单抛异常：{}", ver, apiError);
        }

        // ---------- 5. 判定 ----------
        long ordersAfter = countOrders(uid);
        int stockAfter = currentStock(pid);
        long cartAfter = countCart(uid);

        boolean orderLeft = ordersAfter > ordersBefore;
        boolean stockDeducted = stockAfter < stockBefore;
        boolean cartCleared = cartAfter < cartBefore;
        boolean rolledBack = !orderLeft && !stockDeducted && !cartCleared;

        Map<String, Object> data = new LinkedHashMap<>(16);
        data.put("version", ver);
        data.put("scenario", orderTxSubmitService.describe(ver));
        data.put("apiResult", apiResult);
        data.put("apiError", apiError);

        Map<String, Object> before = new LinkedHashMap<>(4);
        before.put("orders", ordersBefore);
        before.put("stock", stockBefore);
        before.put("cartRows", cartBefore);

        Map<String, Object> after = new LinkedHashMap<>(4);
        after.put("orders", ordersAfter);
        after.put("stock", stockAfter);
        after.put("cartRows", cartAfter);

        data.put("before", before);
        data.put("after", after);
        data.put("orderLeftBehind", orderLeft);      // 订单脏数据
        data.put("stockDeducted", stockDeducted);    // 库存被扣没回滚
        data.put("cartCleared", cartCleared);        // 购物车被清没回滚
        data.put("rolledBack", rolledBack);
        data.put("verdict", rolledBack
                ? "✅ 事务已回滚：订单没留下、库存已还原、购物车还在 —— 数据是干净的"
                : "❌ 事务失效：接口报了错（或假报成功），但数据已经脏了");
        data.put("checkSql", "SELECT id, order_no, total_amount FROM orders WHERE user_id = " + uid
                + "; SELECT id, stock FROM product WHERE id = " + pid
                + "; SELECT id, quantity FROM shopping_cart WHERE user_id = " + uid + ";");
        return data;
    }

    /**
     * 清场 + 造购物车 + 重置库存（一次实验的起点）。
     *
     * <p>⚠️ 这里<b>故意不加 {@code @Transactional}</b>，两个原因：</p>
     * <ol>
     *     <li>这几步彼此独立（删购物车 / 删订单 / 改库存 / 加购物车），
     *         失败重跑一次就行，不需要原子性；</li>
     *     <li>它是被本类的 {@link #run} 用 {@code this.} 调用的 ——
     *         <b>加了注解也不会生效</b>（正是 T4 要讲的那个坑）。
     *         与其写一个"看起来有事务其实没有"的方法，不如干脆不写。</li>
     * </ol>
     */
    private Map<String, Object> resetScene(long userId, long productId, int quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }

        // 清掉该实验用户的购物车与历史订单，让每次实验起点一致
        cleanUserData(userId);

        // 库存给足：2 倍购买量，保证"扣库存"这一步一定成功
        Product stockUpdate = new Product();
        stockUpdate.setId(productId);
        stockUpdate.setStock(100);
        productMapper.updateById(stockUpdate);

        // 下单读的是"当前用户勾选的购物车"，所以这里必须造一条 selected=1 的记录
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setQuantity(quantity);
        cart.setSelected(1);
        shoppingCartMapper.insert(cart);

        log.info("【实验现场已备好】userId={} | productId={} | 商品数=1 | 购买数量={} | 库存={}",
                userId, productId, quantity, quantity * 2);

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("userId", userId);
        data.put("productId", productId);
        data.put("quantity", quantity);
        data.put("stock", 100);
        data.put("message", "现场已备好：购物车 1 条（已勾选）、库存 " + quantity * 2 + " 件");
        return data;
    }

    /** 只清场，不造数据（实验做完收尾用） */
    public Map<String, Object> clean(Long userId) {
        long uid = userId == null ? DEFAULT_USER_ID : userId;
        Map<String, Object> data = new LinkedHashMap<>(cleanUserData(uid));
        data.put("userId", uid);
        data.put("message", "实验数据已清理");
        return data;
    }

    /**
     * 清掉某个用户的购物车 + 订单 + 订单明细。
     * <p>订单明细表没有 user_id，所以要先查出该用户的订单号，再按 order_no 删。</p>
     */
    private Map<String, Object> cleanUserData(long userId) {
        int itemRows = 0;

        List<Orders> orders = ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getUserId, userId));
        if (!orders.isEmpty()) {
            List<String> orderNos = new ArrayList<>(orders.size());
            for (Orders order : orders) {
                orderNos.add(order.getOrderNo());
            }
            itemRows = orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery()
                    .in(OrderItem::getOrderNo, orderNos));
        }
        int orderRows = ordersMapper.delete(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getUserId, userId));
        int cartRows = shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));

        Map<String, Object> rows = new LinkedHashMap<>(4);
        rows.put("orderRows", orderRows);
        rows.put("itemRows", itemRows);
        rows.put("cartRows", cartRows);
        return rows;
    }

    private long countOrders(long userId) {
        Long c = ordersMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getUserId, userId));
        return c == null ? 0L : c;
    }

    private long countCart(long userId) {
        Long c = shoppingCartMapper.selectCount(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));
        return c == null ? 0L : c;
    }

    private int currentStock(long productId) {
        Integer stock = productMapper.selectStock(productId);
        return stock == null ? 0 : stock;
    }

    /** 下单入参 —— 收货信息写死，课堂上不用每次填表单 */
    private OrderSubmitDTO buildDto() {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setReceiverName("事务实验收货人");
        dto.setReceiverPhone("13800000000");
        dto.setReceiverAddress("课堂实验地址（非真实）");
        dto.setRemark("tx-version 事务失效实验");
        return dto;
    }
}
