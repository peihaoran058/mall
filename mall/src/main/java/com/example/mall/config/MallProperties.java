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
        private Long raceWindowMillis = 0L;
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
