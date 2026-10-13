package com.example.mall.mq;

import com.example.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 * MQ 运行状态看板（第20课时演示用）
 * ============================================================================
 * 【讲解顺序 ⑨ · 支撑】看板与实验支撑类，随堂用到再翻（见 mq 包导读）。
 *
 * <p>课堂上最直观的画面：压测跑完，刷新这个接口，能看到
 * <b>落库队列的积压数从"几十"快速掉到 0</b> —— 这就是"削峰填谷"的过程，
 * 比讲十页 PPT 都管用。</p>
 *
 * <p>另外死信队列的积压数是<b>线上必须盯的告警指标</b>：
 * 它一旦不为 0，就说明有消息重试 3 次仍然失败了，需要人工介入。</p>
 *
 * <p><b>为什么"队列深度"比"内存计数器"可信？</b><br>
 * 计数器只活在本实例的内存里（用户发出去的消息自己记账），
 * 多实例部署时每个实例只能看到自己那一份；而队列深度是问 Broker 要的，
 * 全局面貌只有一个。<b>压测看计数器，生产看队列深度</b> —— 这个区别课上要讲清。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqStatusService {

    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    private final MallProperties mallProperties;
    private final MqModeService mqModeService;
    private final OrderMessageTracker tracker;
    private final MqResultStore resultStore;

    /** Broker 地址只用于展示，取不到就给默认值 —— 看板不该因为一个 host 显示不出来就整个 500 */
    @Value("${spring.rabbitmq.host:127.0.0.1}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitPort;

    /**
     * 探测连接 + 汇总所有指标。
     *
     * <p>"连接是否正常"不是靠 ping，而是直接去问一次队列信息：
     * 这一步会真的走一次 Broker 往返，Broker 挂了必然抛异常。
     * 比"启动时连上过就算连上"可靠得多 —— 中间断线也能立刻反映出来。</p>
     */
    public Map<String, Object> status() {
        MallProperties.Mq mq = mallProperties.getMq();

        QueueInformation orderQ = probeQueue(mq.getOrderQueue());
        QueueInformation dlqQ = probeQueue(mq.getDlqQueue());
        boolean connected = orderQ != null || dlqQ != null || brokerReachable();

        Map<String, Object> data = new LinkedHashMap<>(32);

        /* ---------- 连接与拓扑 ---------- */
        data.put("connected", connected);
        data.put("rabbitHost", rabbitHost);
        data.put("rabbitPort", rabbitPort);
        data.put("exchange", mq.getOrderExchange());
        data.put("queue", mq.getOrderQueue());
        data.put("routingKey", mq.getOrderRoutingKey());
        data.put("deadExchange", mq.getDlxExchange());
        data.put("deadQueue", mq.getDlqQueue());
        data.put("messageTtl", mq.getMessageTtl());
        data.put("consumerConcurrency", mq.getConsumerConcurrency());
        data.put("consumerMaxConcurrency", mq.getConsumerMaxConcurrency());
        data.put("prefetch", mq.getPrefetch());

        /* ---------- 当前版本 ---------- */
        int mode = mqModeService.current();
        data.put("mode", mode);
        data.put("modeOverride", mqModeService.runtimeMode());
        data.put("ymlMode", mqModeService.ymlMode());
        data.put("modeDescription", mqModeService.describe(mode));

        /* ---------- 队列积压与在线消费者 ---------- */
        data.put("queueMessageCount", messagesOf(orderQ));
        data.put("queueConsumerCount", consumersOf(orderQ));
        data.put("deadMessageCount", messagesOf(dlqQ));

        /* ---------- 计数器（单实例内存，教学观测用） ---------- */
        Map<String, Object> snapshot = tracker.snapshot();
        data.put("produced", snapshot.get("sent"));
        data.put("consumed", snapshot.get("consumed"));
        data.put("success", snapshot.get("persisted"));
        data.put("failed", snapshot.get("stockNotEnough"));

        /* ---------- 附加信息（前端可选展示，不影响既有契约） ---------- */
        data.put("inFlight", snapshot.get("inFlight"));
        data.put("duplicated", snapshot.get("duplicate"));
        data.put("compensated", snapshot.get("compensated"));
        data.put("error", snapshot.get("error"));
        data.put("gateRejected", snapshot.get("gateRejected"));
        data.put("resultCacheSize", resultStore.size());
        data.put("logs", tracker.logs());

        return data;
    }

    /**
     * 清空业务队列 + 死信队列，并把计数器、结果缓存一起归零。
     *
     * <p>课堂上每跑一轮实验前都要"恢复现场"，否则上一轮的积压和计数会污染这一轮的结论。
     * 注意这里<b>不清 Redis 库存</b>：库存归位是洪峰实验自己的第一步（它会按 mode 预热或清除），
     * 两件事混在一起反而不好解释。</p>
     */
    public Map<String, Object> purge() {
        Map<String, Object> purged = new LinkedHashMap<>(8);
        int orderPurged = purgeQueue(mallProperties.getMq().getOrderQueue());
        int dlqPurged = purgeQueue(mallProperties.getMq().getDlqQueue());
        purged.put("orderQueue", orderPurged);
        purged.put("deadQueue", dlqPurged);

        tracker.reset();
        resultStore.clear();

        log.info("【课堂实验】清空 MQ 队列并归零统计 | 业务队列 {} 条 | 死信队列 {} 条", orderPurged, dlqPurged);

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("purged", purged);
        data.put("message", "已清空业务队列与死信队列，并把统计归零");
        return data;
    }

    /* ==================== 内部工具 ==================== */

    /** 查一个队列的信息，Broker 不可达时返回 null（不抛异常，看板要能显示"未连接"） */
    private QueueInformation probeQueue(String queueName) {
        try {
            return amqpAdmin.getQueueInfo(queueName);
        } catch (Exception e) {
            log.debug("查询队列信息失败（Broker 可能未启动）| queue={} | {}", queueName, e.getMessage());
            return null;
        }
    }

    /** 队列不存在 / Broker 挂掉时，用一个轻量调用判断 Broker 是否可达 */
    private boolean brokerReachable() {
        // 直接开一条 TCP 连接：这是最不容易骗人的探测方式（连接建立失败 = Broker 不可用）。
        // 不用 amqpAdmin.getQueueNames()，因为它在部分 spring-amqp 版本里并不在 AmqpAdmin 接口上。
        try (Connection connection = connectionFactory.createConnection()) {
            return connection.isOpen();
        } catch (Exception e) {
            log.debug("探测 RabbitMQ 连接失败 | {}", e.getMessage());
            return false;
        }
    }

    private Object messagesOf(QueueInformation info) {
        return info == null ? null : info.getMessageCount();
    }

    private Object consumersOf(QueueInformation info) {
        return info == null ? null : info.getConsumerCount();
    }

    /** 清空一个队列，返回清掉的条数；队列不存在或 Broker 挂了返回 0 */
    private int purgeQueue(String queueName) {
        try {
            return amqpAdmin.purgeQueue(queueName);
        } catch (Exception e) {
            log.warn("清空队列失败（队列可能不存在或 Broker 未启动）| queue={} | {}", queueName, e.getMessage());
            return 0;
        }
    }
}
