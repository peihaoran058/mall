package com.example.mall.controller;

import com.example.mall.common.Constants;
import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.Orders;
import com.example.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 订单接口（第10、12课时）。
 *
 * <p>完整业务链路：<br>
 * 加购物车 → {@code POST /api/order/submit} 下单 →
 * {@code POST /api/order/{orderNo}/pay} 模拟支付 →
 * {@code POST /api/order/{orderNo}/cancel} 取消订单（回滚库存）</p>
 *
 * <p><b>第20课时新增：秒杀异步下单（RabbitMQ）</b></p>
 * <ul>
 *     <li>{@code POST /api/order/mq/submit} —— 投递消息，立刻返回"排队中"</li>
 *     <li>{@code GET  /api/order/mq/result/{orderNo}} —— 轮询异步下单的最终结果</li>
 * </ul>
 * <p>为什么要给异步下单单独开接口，而不把它塞进 {@code /api/order/submit}？<br>
 * 因为两者的<b>返回语义完全不同</b>：同步下单返回"订单已创建"，
 * 异步下单只能返回"已进入队列"。接口语义变了就应该换接口，
 * 让调用方一眼看出"这个接口拿到的不一定是最终结果"——这比用同一个 URL
 * 靠配置偷偷切换行为要诚实得多，也少踩坑。</p>
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    //private final OrderMqService orderMqService;


    @PostMapping("/submit")
    public Result<Orders> submit(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                @RequestBody @Valid OrderSubmitDTO dto) {
        return Result.success("下单成功", orderService.submit(userId, dto));
    }

    /** 我的订单分页 */
    @GetMapping("/page")
    public Result<PageResult<Orders>> page(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                          OrderQueryDTO query) {
        return Result.success(orderService.myPage(userId, query));
    }

    /** 订单详情（含商品明细） */
    @GetMapping("/{orderNo}")
    public Result<Orders> detail(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                @PathVariable String orderNo) {
        return Result.success(orderService.detail(userId, orderNo));
    }

    /** 模拟支付 */
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                           @PathVariable String orderNo) {
        orderService.pay(userId, orderNo);
        return Result.success("支付成功（模拟）", null);
    }

    /** 取消订单：状态改为已取消，并把库存加回商品表 */
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                              @PathVariable String orderNo) {
        orderService.cancel(userId, orderNo);
        return Result.success("订单已取消，库存已回滚", null);
    }

    /* ==================================================================
     * 第20课时：秒杀异步下单（RabbitMQ）
     * ================================================================== */

    /**
     * 秒杀下单：把请求丢进 MQ，立刻返回"排队中"。
     *
     * <p><b>和 {@code /submit} 最大的差别：</b>这个接口返回里<b>没有订单实体</b>，
     * 只有一个 {@code orderNo} 和 {@code status=QUEUING}。
     * 因为此刻订单还没落库 —— 真正下单的是消费者。
     * 前端必须拿着 orderNo 去轮询下面的结果接口。</p>
     *
     * <p>课堂对比实验：先在前面那套 Node 网关压测里压 {@code /api/order/submit}
     * （同步，200ms+ 才返回），再用秒杀页点几下 —— 接口几毫秒就回来了，
     * 剩下的事都在队列里。<b>这就是"削峰"：把用户等待时间从"数据库处理完"
     * 变成"消息进队列"。</b></p>
     */
    /*@PostMapping("/mq/submit")
    public Result<Map<String, Object>> mqSubmit(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                               @RequestBody @Valid SeckillSubmitDTO dto) {
        Map<String, Object> data = orderMqService.submit(userId, dto.getProductId(), dto.getQuantity(),
                dto.getReceiverName(), dto.getReceiverPhone(), dto.getReceiverAddress(), dto.getRemark());
        return Result.success("已进入队列，正在排队下单", data);
    }*/

    /**
     * 轮询秒杀下单结果。
     *
     * <p>异步下单的"最后一公里"：接口返回"排队中"之后，
     * 用户怎么知道到底买没买到？就是靠这个接口。
     * 不要小看它 —— 很多项目上了 MQ 却忘了做结果回查，
     * 用户点完秒杀就一脸茫然，<b>体验比同步下单还差</b>。</p>
     */
    /*@GetMapping("/mq/result/{orderNo}")
    public Result<Map<String, Object>> mqResult(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                               @PathVariable String orderNo) {
        return Result.success(orderMqService.result(orderNo, userId));
    }*/
}
