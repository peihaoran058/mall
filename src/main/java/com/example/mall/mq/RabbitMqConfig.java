package com.example.mall.mq;

import com.example.mall.config.MallProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * RabbitMQ 拓扑与序列化配置（第20课时）
 * ============================================================================
 * 【讲解顺序 ② · 公共拓扑】交换机 / 队列 / 死信 / JSON 转换器 / 发送方确认。
 * 三个版本共用这套拓扑，V6 之前先讲它，后面就不用再回来。
 *
 * <pre>
 *  ┌────────────────────┐  mall.order.submit  ┌──────────────────────┐
 *  │ mall.order         │ ──────────────────> │ mall.order.queue     │──> 消费者落库
 *  │  .exchange         │                     │ (DLX: ...dead.       │
 *  └────────────────────┘                     │       exchange)      │
 *                                             └──────────┬───────────┘
 *                                          重试 3 次仍失败 / 消息 TTL 到期
 *                                                        ▼
 *                                          ┌──────────────────────────┐
 *                                          │ mall.order.dead.queue    │  人工排查
 *                                          └──────────────────────────┘
 * </pre>
 *
 * <p><b>课上要讲清的三件事：</b></p>
 *
 * <p><b>1. 为什么用 Direct 交换机而不是自己往队列里发？</b><br>
 * 生产者直接指定队列名，看似简单，但生产者和消费者就被队列名<b>硬绑定</b>了：
 * 想加一个"同时统计销量"的消费者，就得改生产者代码。
 * 引入交换机后，生产者只管往交换机发（带一个 routingKey），
 * 谁要收消息谁自己来绑定，生产端完全不用知道下游有几个消费者。</p>
 *
 * <p><b>2. 队列为什么要 durable=true、消息为什么要持久化？</b><br>
 * {@code durable=true} 让队列在 RabbitMQ 重启后还在；加上 Spring 默认把消息标记为
 * 持久化（deliveryMode=2），消息才会落盘。两个都做才有效 ——
 * 只设队列持久化、消息非持久化，MQ 重启后队列还在但里面是空的。</p>
 *
 * <p><b>3. 死信队列（DLQ）不是可选项。</b><br>
 * 消息消费失败后只有两个去处：重回队列（反复重投，日志刷屏，永远消费不掉，
 * 甚至把消费者线程拖死）或者丢弃（数据静默丢失）。
 * 配死信队列后走第三条路：<b>重试耗尽 → 转入 DLQ 等人工处理</b>，
 * 消息不丢、也不影响正常队列。这就是 application.yml 里
 * {@code default-requeue-rejected: false} 配合 {@code x-dead-letter-exchange} 的效果。</p>
 *
 * <p>顺带一提：队列上还挂了 {@code x-message-ttl}（见 {@code mall.mq.message-ttl}）。
 * 大量消息堆积超过 TTL 会被判为"死信"转进 DLQ，相当于给积压上了一道保险 ——
 * 但要注意这也意味着<b>堆积太久的数据会被丢掉</b>，TTL 值必须按业务容忍度来定。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final MallProperties mallProperties;

    /* ==================== 业务交换机 / 队列 / 绑定 ==================== */

    @Bean
    public DirectExchange orderExchange() {
        // durable=true：MQ 重启后交换机还在
        return new DirectExchange(mallProperties.getMq().getOrderExchange(), true, false);
    }

    @Bean
    public Queue orderQueue() {
        Map<String, Object> args = new HashMap<>(4);
        // 死信路由：本队列里的消息被 reject 且不重回队列时，转投到这个交换机
        args.put("x-dead-letter-exchange", mallProperties.getMq().getDlxExchange());
        args.put("x-dead-letter-routing-key", mallProperties.getMq().getDlqRoutingKey());
        // 消息 TTL：堆积过久的消息自动变成死信转进 DLQ，避免队列无限积压
        args.put("x-message-ttl", mallProperties.getMq().getMessageTtl());
        return new Queue(mallProperties.getMq().getOrderQueue(), true, false, false, args);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange)
                .with(mallProperties.getMq().getOrderRoutingKey());
    }

    /* ==================== 死信交换机 / 队列 / 绑定 ==================== */

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(mallProperties.getMq().getDlxExchange(), true, false);
    }

    @Bean
    public Queue dlqQueue() {
        // 死信队列不用再配 DLX —— 再挂一层就会无限循环了
        return new Queue(mallProperties.getMq().getDlqQueue(), true, false, false);
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlqQueue).to(dlxExchange)
                .with(mallProperties.getMq().getDlqRoutingKey());
    }

    /* ==================== 消息序列化 ==================== */

    /**
     * 用 JSON 而不是 JDK 序列化。
     *
     * <p>JDK 序列化的消息在 RabbitMQ 管理界面里是一堆二进制乱码，排查问题基本没法看；
     * JSON 能直接读到内容和字段，课堂上让学生自己打开管理界面看消息体，效果最好。</p>
     *
     * <p><b>⚠️ 这里最容易踩的坑：</b>不能让 Jackson 用默认的 ObjectMapper，
     * 因为消息里带着 {@link com.example.mall.entity.Orders}，其中
     * {@code createTime / updateTime} 是 {@code LocalDateTime}。
     * 默认 ObjectMapper 不认识 java.time 类型，序列化时直接抛
     * {@code InvalidDefinitionException: Java 8 date/time type not supported by default}。
     * 必须注册 {@code JavaTimeModule}。</p>
     *
     * <p>另外这里<b>不开</b> default typing（不像 Redis 那样写类型信息），
     * 因为消费者方法签名已经声明了参数类型，靠 message 的
     * {@code __TypeId__} 头就够了，不用在 JSON 里塞 {@code @class} 字段。</p>
     */
    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 时间写成 "2026-10-12 17:00:00" 这种可读字符串，而不是时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * 发送方可靠性：自己声明 RabbitTemplate，挂上序列化器 + 确认回调 + 退回回调。
     *
     * <p>消息"发出去没报错"不等于"真的进了队列"：</p>
     * <ul>
     *     <li>可能压根没到交换机（网络断、MQ 挂了）→ {@code ConfirmCallback} 的 ack=false；</li>
     *     <li>到了交换机但没有队列绑定这个 routingKey（路由失败）→
     *         配了 {@code mandatory=true} + {@code ReturnsCallback} 才会退回来，
     *         否则消息被<b>静默丢弃</b>，这是最坑的一种"丢单"。</li>
     * </ul>
     *
     * <p><b>⚠️ 版本坑（容易查半天）：</b>Spring Boot 3.x 提供了
     * {@code RabbitTemplateCustomizer} 接口来定制自动装配的 RabbitTemplate，
     * 但 <b>2.7 里没有这个类</b>（只有 {@code ConnectionFactoryCustomizer} 和
     * {@code RabbitRetryTemplateCustomizer}）。2.7 的做法就是像下面这样
     * <b>直接声明 RabbitTemplate Bean</b>：Spring Boot 的自动配置带
     * {@code @ConditionalOnMissingBean}，看到我们自己定义了就不再创建，
     * 所有设置以这里的为准。</p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 路由不到队列时把消息退回给发送方（而不是静默丢弃）
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("【MQ】消息未到达交换机！需人工介入 | cause={} | correlation={}",
                        cause, correlationData);
            }
        });

        template.setReturnsCallback(returned -> log.error(
                "【MQ】消息路由失败被退回（没有队列绑定这个 routingKey）| exchange={} | routingKey={} | reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));

        return template;
    }
}
