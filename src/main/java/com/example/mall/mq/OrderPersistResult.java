package com.example.mall.mq;

/**
 * 落库结果（原 {@code OrderPersistService.Result}，V6/V7 落库拆成两个版本类后独立成枚举）。
 *
 * <p>消费者 {@link OrderMqConsumer} 据此决定：算成功还是失败、
 * 要不要回补 Redis（V8 专属补偿）。</p>
 */
public enum OrderPersistResult {

    /** 落库成功 */
    PERSISTED,

    /** 消息重复，已存在同 id 订单，幂等跳过（业务上算成功） */
    DUPLICATE,

    /** 库存不足，被拦下（业务失败；V8 里还要回补 Redis） */
    STOCK_NOT_ENOUGH
}
