package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 提交订单入参。
 *
 * <p>注意这里<b>没有商品列表、也没有金额</b>。
 * 金额一律由服务端根据购物车 + 商品表重新计算，
 * 前端传过来的价格只用于展示，绝不参与计算 ——
 * 否则用户改一下请求体就能 1 分钱买手机，这是最经典的电商漏洞。</p>
 */
@Data
public class OrderSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货电话格式不正确")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    private String remark;
}
