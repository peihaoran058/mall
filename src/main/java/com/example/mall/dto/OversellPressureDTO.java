package com.example.mall.dto;

import lombok.Data;

/**
 * 并发压测请求参数（前端演示台 POST /api/demo/pressure/run 的 body）。
 *
 * <p>老版压测还带 userPrefix（每个并发注册一个独立买家，走购物车结算）；
 * 现在压测直接在后端内存里并发调下单方法，不走购物车，
 * 一个共享压测账号就够了，userPrefix 就不需要了。</p>
 */
@Data
public class OversellPressureDTO {

    /** 压测哪个商品 */
    private Long productId = 1L;

    /** 压测前把库存重置为这个值 */
    private Integer stock = 10;

    /** 并发数（同时发起多少个下单请求），限制在 2~100 */
    private Integer concurrency = 20;
}
