package com.example.mall.mq;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【讲解顺序 ⑨ · 支撑】三态结果：QUEUING / SUCCESS / FAILED，前端轮询的返回体。
 * 秒杀排队结果（前端轮询 {@code GET /api/order/mq/result/{orderNo}} 的返回体）。
 *
 * <p>三个状态和前端 {@code MQ_ORDER_STATUS_MAP} 一一对应：</p>
 * <table border="1">
 *     <caption>状态三态</caption>
 *     <tr><th>status</th><th>含义</th><th>触发时机</th></tr>
 *     <tr><td>QUEUING</td><td>还在队列里 / 正在处理</td><td>入口投递成功，立刻写入</td></tr>
 *     <tr><td>SUCCESS</td><td>下单成功，订单已落库</td><td>消费者落库成功</td></tr>
 *     <tr><td>FAILED</td><td>下单失败（通常是库存不足）</td><td>消费者判定库存不足</td></tr>
 * </table>
 *
 * <p><b>⚠️ FAILED 是"业务失败"，不是"系统故障"。</b>库存不足时消息已经被正常消费，
 * 消费端会把它 ack 掉（而不是丢回队列重试），否则这条消息会在队列里
 * 无限循环重试，把消费者线程和 CPU 一起打满。</p>
 *
 * <p>字段和前端对齐：{@code status / message / stock / mode / orderNo}，
 * 另外多给 {@code orderId} 方便课堂上去数据库里核对。</p>
 */
@Data
public class MqOrderResult {

    public static final String STATUS_QUEUING = "QUEUING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String orderNo;

    private String status;

    /** 给用户看的一句话说明 */
    private String message;

    /** 处理这条消息时生效的消费端版本（6/7/8） */
    private Integer mode;

    /** 结果落定那一刻的商品库存（QUEUING 时是投递时的快照，可能为 null） */
    private Integer stock;

    private Long orderId;

    /** 写入时间戳，只用于内部 TTL 清理，不返回给前端 */
    private long updatedAt = System.currentTimeMillis();

    public static MqOrderResult queuing(String orderNo, Integer mode, Integer stock, Long orderId) {
        return of(orderNo, STATUS_QUEUING, "已进入队列，正在排队下单…", mode, stock, orderId);
    }

    public static MqOrderResult success(String orderNo, Integer mode, Integer stock, Long orderId) {
        return of(orderNo, STATUS_SUCCESS, "抢到了！订单已创建（异步落库完成）", mode, stock, orderId);
    }

    public static MqOrderResult failed(String orderNo, Integer mode, Integer stock, Long orderId) {
        return of(orderNo, STATUS_FAILED, "没抢到：库存不足（业务失败，消息已被正常消费）", mode, stock, orderId);
    }

    private static MqOrderResult of(String orderNo, String status, String message,
                                    Integer mode, Integer stock, Long orderId) {
        MqOrderResult result = new MqOrderResult();
        result.setOrderNo(orderNo);
        result.setStatus(status);
        result.setMessage(message);
        result.setMode(mode);
        result.setStock(stock);
        result.setOrderId(orderId);
        return result;
    }

    /** 返回给前端的结构（不暴露 updatedAt 这种内部字段） */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("orderNo", orderNo);
        data.put("status", status);
        data.put("message", message);
        data.put("mode", mode);
        data.put("stock", stock);
        data.put("orderId", orderId);
        return data;
    }
}
