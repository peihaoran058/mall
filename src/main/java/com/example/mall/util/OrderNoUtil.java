package com.example.mall.util;

import com.example.mall.common.Constants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号生成工具。
 *
 * <p><b>课上重点：订单号为什么不能用自增 id？</b></p>
 * <ul>
 *     <li>自增 id 会暴露业务量（对手看你的订单号就知道你一天卖多少单）</li>
 *     <li>分库分表后自增 id 会冲突</li>
 *     <li>订单号要能被运营"看出来"下单时间，方便排查</li>
 * </ul>
 *
 * <p>本工具提供三种方案，课上对比：</p>
 * <ol>
 *     <li>{@link #uuid()} —— 笔记里的写法，简单但字符串太长、无序、可读性差</li>
 *     <li>{@link #timeRandom()} —— 项目默认：时间 + 随机数，可读且基本不会撞</li>
 *     <li>{@link #snowflakeLike()} —— 雪花算法思路演示（19 位纯数字，趋势递增）</li>
 * </ol>
 *
 * <p><b>注意：</b>Random 在多线程下是有竞争的（CAS 自旋），
 * 高并发场景要用 {@link ThreadLocalRandom}，这也是一个可以讲的性能细节。</p>
 */
public class OrderNoUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private OrderNoUtil() {
    }

    /** 方案一：笔记里的 UUID 写法 */
    public static String uuid() {
        return Constants.ORDER_NO_PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 方案二（项目默认）：MALL + yyyyMMddHHmmss + 6 位随机数
     * <p>长度 24，可读性好，单机每秒万级不会重复。</p>
     */
    public static String timeRandom() {
        String time = LocalDateTime.now().format(FMT);
        int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return Constants.ORDER_NO_PREFIX + time + random;
    }

    /**
     * 方案三：类雪花算法（时间戳 + 机器位 + 序列号），趋势递增，18~19 位纯数字。
     * <p>生产环境直接用 Hutool 的 {@code IdUtil.getSnowflake()} 或 MyBatis-Plus 的
     * {@code ASSIGN_ID}（雪花算法实现）即可，不用自己写。</p>
     */
    public static long snowflakeLike() {
        long timestamp = System.currentTimeMillis();
        long workerId = 1L;
        long seq = SEQ.getAndIncrement() & 0xFFF;
        return ((timestamp - 1600000000000L) << 22) | (workerId << 12) | seq;
    }
}
