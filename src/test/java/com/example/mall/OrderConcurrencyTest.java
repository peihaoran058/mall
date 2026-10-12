package com.example.mall;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.config.MallProperties;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.ShoppingCartMapper;
import com.example.mall.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================================
 * 并发超卖压测用例（第13 ~ 16课时的"实验仪器"）
 * ============================================================================
 *
 * <p><b>用法（在 IDEA 里右键运行，或命令行）：</b></p>
 * <pre>
 * mvn test -DskipTests=false -Dtest=OrderConcurrencyTest
 * </pre>
 *
 * <p><b>实验步骤：</b></p>
 * <ol>
 *     <li>准备 20 个用户（id 1001~1020），每人购物车里放 1 件"商品1"</li>
 *     <li>把商品 1 的库存重置为 10</li>
 *     <li>20 个线程用 CountDownLatch 同时开始下单</li>
 *     <li>统计成功/失败数量，并打印最终库存</li>
 * </ol>
 *
 * <p><b>预期结果：</b></p>
 * <ul>
 *     <li>version = 1（无锁）→ 成功数 &gt; 10，库存为<b>负数</b>，超卖复现</li>
 *     <li>version = 2（事务+锁同方法）→ 依然负库存，锁失效</li>
 *     <li>version = 3/4/5 → 成功数正好 = 10，库存正好 = 0</li>
 * </ul>
 *
 * <p>⚠️ 这个用例会真实读写数据库，<b>只在本机开发环境跑</b>。</p>
 */
@SpringBootTest
class OrderConcurrencyTest {

    /** 测试商品 id（库存被压测的那个） */
    private static final Long PRODUCT_ID = 1L;

    /** 初始库存：10 件 */
    private static final int INIT_STOCK = 10;

    /** 并发线程数：20 个用户同时抢 10 件 */
    private static final int THREAD_COUNT = 20;

    /** 测试用户 id 起点（避开初始化数据里的 1、2 号用户） */
    private static final long USER_ID_BASE = 1001L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ShoppingCartMapper cartMapper;

    @Autowired
    private MallProperties mallProperties;

    @Test
    @DisplayName("并发下单压测：观察是否超卖")
    void testOversell() throws Exception {
        System.out.println("\n==================== 并发压测开始 ====================");
        System.out.println("当前下单版本：V" + mallProperties.getOrder().getVersion()
                + "   " + describeVersion(mallProperties.getOrder().getVersion()));
        System.out.println("竞态窗口延迟：" + mallProperties.getOrder().getRaceWindowMillis() + " ms");
        System.out.println("商品 " + PRODUCT_ID + " 初始库存：" + INIT_STOCK + "，并发线程：" + THREAD_COUNT);

        // ---------- 1. 准备数据 ----------
        prepare();

        // ---------- 2. 并发下单 ----------
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        // ready 门闩：确保 20 个线程都就绪了再一起冲
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        // start 门闩：一声令下同时起跑，制造最大并发
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final long userId = USER_ID_BASE + i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    orderService.submit(userId, buildOrderDTO());
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    System.out.println("  下单失败 | userId=" + userId + " | " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long begin = System.currentTimeMillis();
        start.countDown();          // 发令枪
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();
        long cost = System.currentTimeMillis() - begin;

        // ---------- 3. 统计结果 ----------
        Product product = productMapper.selectById(PRODUCT_ID);
        Long orderCount = ordersMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .likeRight(Orders::getOrderNo, "MALL"));
        Long itemCount = orderItemMapper.selectCount(Wrappers.<OrderItem>lambdaQuery()
                .likeRight(OrderItem::getOrderNo, "MALL"));

        int stock = product.getStock() == null ? 0 : product.getStock();

        System.out.println("\n-------------------- 压测结果 --------------------");
        System.out.println("耗时            ：" + cost + " ms");
        System.out.println("下单成功        ：" + success.get() + " 单");
        System.out.println("下单失败        ：" + fail.get() + " 单（库存不足被正确拦下）");
        System.out.println("订单表记录数    ：" + orderCount);
        System.out.println("订单明细记录数  ：" + itemCount);
        System.out.println("商品最终库存    ：" + stock + "（初始 " + INIT_STOCK + "）");
        if (stock < 0) {
            System.out.println("\n⚠️⚠️⚠️  超卖复现！库存变成负数：" + stock
                    + "，成功下单 " + success.get() + " 单 > 库存 " + INIT_STOCK + " 件");
            System.out.println("👉 这就是竞态条件：多个线程读到同一份旧库存，都认为「还有货」");
        } else {
            System.out.println("\n✅ 没有超卖。成功 " + success.get() + " 单，库存剩余 " + stock);
        }
        System.out.println("==================================================\n");

        cleanUp();
    }

    /* ==================== 辅助方法 ==================== */

    /** 重置库存、清理订单、为 20 个用户各准备一条购物车记录 */
    private void prepare() {
        // 重置库存
        Product update = new Product();
        update.setId(PRODUCT_ID);
        update.setStock(INIT_STOCK);
        update.setSales(0);
        productMapper.updateById(update);

        // 清理历史订单，保证每次实验结果可对比
        orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery().likeRight(OrderItem::getOrderNo, "MALL"));
        ordersMapper.delete(Wrappers.<Orders>lambdaQuery().likeRight(Orders::getOrderNo, "MALL"));

        // 每个用户一条购物车记录：quantity = 1
        for (int i = 0; i < THREAD_COUNT; i++) {
            long userId = USER_ID_BASE + i;
            cartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getProductId, PRODUCT_ID));

            ShoppingCart cart = new ShoppingCart();
            cart.setUserId(userId);
            cart.setProductId(PRODUCT_ID);
            cart.setQuantity(1);
            cart.setSelected(1);
            cartMapper.insert(cart);
        }
        System.out.println("数据准备完成：20 个用户各 1 件商品已放入购物车");
    }

    /** 清理本次压测产生的数据，避免污染后续课程 */
    private void cleanUp() {
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            userIds.add(USER_ID_BASE + i);
        }
        cartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery().in(ShoppingCart::getUserId, userIds));
        System.out.println("已清理测试用户的购物车数据（订单数据保留，方便课堂查看）");
    }

    private OrderSubmitDTO buildOrderDTO() {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setReceiverName("压测用户");
        dto.setReceiverPhone("13800000000");
        dto.setReceiverAddress("测试地址");
        dto.setRemark("并发压测");
        return dto;
    }

    private String describeVersion(Integer version) {
        if (version == null) {
            return "未配置";
        }
        switch (version) {
            case 2:
                return "V2 错误示范：@Transactional + synchronized 同方法，预期依然超卖";
            case 3:
                return "V3 单机锁：锁在外层、事务在内层，预期不超卖";
            case 4:
                return "V4 Redis 分布式锁，预期不超卖";
            case 5:
                return "V5 数据库原子扣减，预期不超卖";
            case 1:
            default:
                return "V1 无锁版本，预期必然超卖";
        }
    }
}
