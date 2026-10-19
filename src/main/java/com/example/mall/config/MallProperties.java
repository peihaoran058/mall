package com.example.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 项目自定义配置绑定。
 *
 * <p>为什么不用 {@code @Value("${mall.order.version}")} 到处取？<br>
 * 一是散落在各处难以维护，二是 {@code @Value} 拼错 key 启动时才报错。
 * 集中绑定成一个对象后，IDE 能提示、类型也安全。</p>
 *
 * <p><b>这也是本项目"版本迭代"的开关</b>：改一行 yml 就能把下单实现
 * 从 V1 无锁切到 V4 分布式锁，课堂上现场演示超卖 → 修复的全过程。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall")
public class MallProperties {

    private Order order = new Order();
    private Mq mq = new Mq();
    private Stock stock = new Stock();
    private Upload upload = new Upload();
    private Cache cache = new Cache();

    @Data
    public static class Order {
        /** 下单实现版本：1 无锁 / 2 事务内加锁（错误） / 3 单机锁正确版 / 4 Redis 分布式锁 */
        private Integer version = 1;
        /**
         * 【教学开关】事务失效实验版本（第 10~11 课时），默认 0 = 不启用。
         * <p>和 {@link #version} 是两个独立的开关，互不干扰：</p>
         * <ul>
         *     <li>{@code version} 1~5：并发超卖专题（第 13~16 课时）</li>
         *     <li>{@code txVersion} 1~5：事务失效专题（第 10~11 课时）</li>
         * </ul>
         * <p>1 = 正确版（事务 + rollbackFor，异常整体回滚）；
         * 2 = 漏写 rollbackFor + 受检异常；3 = 异常被 try-catch 吃掉；
         * 4 = 同类自调用；5 = @Transactional 写在 private 方法上。
         * <b>2~5 都是故意写错的版本</b>，跑完会把脏数据留在 orders / product 里，
         * 演示完记得调 {@code POST /api/demo/tx/clean} 收尾。</p>
         * <p>课堂上也可以不改 yml，用 {@code POST /api/demo/tx/mode?value=2} 热切换。</p>
         */
        private Integer txVersion = 0;
        /** Redis 锁持有时长（秒） */
        private Long lockTimeout = 10L;
        /** 获取 Redis 锁的最大等待时间（毫秒） */
        private Long lockWaitMillis = 3000L;
        /**
         * 【教学开关】在"读库存"和"扣库存"之间人为插入的延迟（毫秒），默认 0。
         * <p>真实项目里这个竞态窗口来自网络往返、GC、SQL 执行时间，天然存在；
         * 但如果本地 MySQL 太快、压测没复现出超卖，把它调成 5~10 就能稳定复现。
         * 演示完记得调回 0。</p>
         */
        private Long raceWindowMillis = 10L;
    }

    /**
     * 第20课时：秒杀异步下单（RabbitMQ）。
     *
     * <p>和 {@link Order} 是<b>两个互不干扰的开关</b>：</p>
     * <pre>
     * mall.order.version      1~5   并发超卖专题（第 13~16 课时）
     * mall.order.tx-version   1~5   事务失效专题（第 10~11 课时）
     * mall.mq.mode            6~8   秒杀异步下单（第 20 课时）★ 本类
     * </pre>
     *
     * <p><b>为什么拓扑名（交换机/队列名）也放在配置里？</b><br>
     * 生产端和消费端必须用同一套名字。硬编码在代码里的话，
     * 改队列名要改两个类，漏一个就变成"发到 A 队列、监听 B 队列"——
     * 消息发出去看着成功，其实永远没人消费，排查半天。
     * 集中成一处配置，改的时候两边一起变。</p>
     */
    @Data
    public static class Mq {

        /**
         * 消费端版本（6/7/8），也可以说"秒杀链路的实现版本"：
         * <ul>
         *     <li>{@code 6} 只有 MQ：入口直接投递，消费端"读库存 → Java 判断 → 无条件扣减"，
         *         并发消费时依然超卖 —— <b>用来证明 MQ 解决的是削峰，不解决超卖</b></li>
         *     <li>{@code 7} MQ + 幂等 + 数据库原子扣减：不超卖，但瞬时洪峰会全量投进队列</li>
         *     <li>{@code 8} 最终版：Redis Lua 预扣库存 + MQ 异步落库 + 数据库兜底</li>
         * </ul>
         */
        private Integer mode = 7;

        /* ---------------- 拓扑（交换机 / 队列名） ---------------- */

        /** 业务交换机 */
        private String orderExchange = "mall.order.exchange";
        /** 落库队列（消费者监听它） */
        private String orderQueue = "mall.order.queue";
        /** 落库路由键 */
        private String orderRoutingKey = "mall.order.submit";
        /** 死信交换机 */
        private String dlxExchange = "mall.order.dead.exchange";
        /** 死信队列（重试耗尽后的消息落到这里等人工排查） */
        private String dlqQueue = "mall.order.dead.queue";
        /** 死信路由键 */
        private String dlqRoutingKey = "mall.order.dead";

        /* ---------------- 消费行为（同一份值同时驱动监听容器与前端看板） ---------------- */

        /** 消费者最小线程数 */
        private Integer consumerConcurrency = 3;
        /** 积压时自动扩容到的上限 —— 削峰填谷的关键 */
        private Integer consumerMaxConcurrency = 10;
        /** 每次预取多少条（1 = 取一条处理完再取下一条，避免消费端内存堆积） */
        private Integer prefetch = 1;

        /* ---------------- 教学参数 ---------------- */

        /** 队列消息 TTL（毫秒）：堆积过久自动进死信，避免无限积压 */
        private Long messageTtl = 600000L;

        /**
         * 【教学开关】V6 专用的竞态窗口（毫秒），默认 30。
         *
         * <p>V6 的超卖来自消费端"读库存 → Java 判断 → 扣库存"三步分离。
         * 但消费端线程是从 MQ 监听容器线程池里起来的，本地 MySQL 又太快，
         * 三条线程很可能压根没重叠（一个事务提交完，另一个才开始读），
         * 于是 V6 压不出超卖 —— 课堂上会很尴尬。</p>
         *
         * <p>所以在"读"和"扣"之间显式睡一会儿，把窗口放大，让 BUG <b>稳定复现</b>。
         * 真实项目里这个窗口来自网络往返 / GC / SQL 执行时间，本来就存在。
         * 想演示"理想串行执行也不该用三步分离"，把它改成 0 即可。</p>
         */
        private Long raceWindowMillis = 30L;

        /** 秒杀结果缓存（前端轮询用）保留时长（秒） */
        private Long resultTtlSeconds = 600L;

        /** 消息幂等键（Redis SETNX）保留时长（秒） */
        private Long idempotentTtlSeconds = 600L;

        /** 洪峰实验等待"队列吃干净"的最长时间（毫秒） */
        private Long floodTimeoutMillis = 30000L;
    }

    /**
     * 秒杀库存（Redis 侧）。
     *
     * <p>为什么还要单独一段配置？Redis 里的库存是<b>DB 库存的副本</b>，
     * 副本的 key 命名必须集中管理 —— 预热写的是哪个 key、预扣扣的是哪个 key、
     * 对账扫的是哪个 key，三者只要有一个对不上，就会出现
     * "明明预热了却报未预热"这种最难查的问题。</p>
     */
    @Data
    public static class Stock {
        /** Redis 库存 key 前缀，完整 key = 前缀 + productId */
        private String keyPrefix = "mall:stock:product:";
    }

    @Data
    public static class Upload {
        /** 本地存储根目录 */
        private String path;
        /** 访问 URL 前缀 */
        private String urlPrefix = "/upload/";
    }

    @Data
    public static class Cache {
        /** 商品详情缓存秒数 */
        private Long productDetailTtl = 600L;
    }
}
