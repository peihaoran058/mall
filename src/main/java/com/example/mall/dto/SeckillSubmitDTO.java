package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 秒杀下单入参（{@code POST /api/order/mq/submit}）。
 *
 * <p>和 {@link OrderSubmitDTO}（购物车结算）最大的差别：</p>
 * <ul>
 *     <li><b>没有购物车</b>：秒杀是"直接抢这一件"，指定 {@code productId + quantity}；</li>
 *     <li><b>收货信息可空</b>：秒杀拼的是手速，让用户先填地址再抢是不现实的。
 *         真实秒杀系统会从用户资料里预填，这里为空时由
 *         {@code MqOrderFactory} 填演示默认值。</li>
 * </ul>
 *
 * <p>金额同样<b>不由前端传</b>：服务端按商品表的当前价格计算，
 * 否则改一下请求体就能 1 分钱买手机 —— 这是最经典的电商漏洞。</p>
 */
@Data
public class SeckillSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 抢哪个商品 */
    @NotNull(message = "商品不能为空")
    private Long productId;

    /**
     * 抢购数量。
     * <p>演示环境限制 1~5 件：秒杀的真实业务里通常限购 1 件，
     * 数量开大只会让"超卖"看起来更乱，讲不清问题。</p>
     */
    @Min(value = 1, message = "至少抢购 1 件")
    @Max(value = 5, message = "演示环境最多抢购 5 件")
    private Integer quantity = 1;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;
}
