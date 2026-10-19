package com.example.mall.order;

import com.example.mall.entity.OrderLog;
import com.example.mall.mapper.OrderLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 订单异步服务（第15课时）。
 *
 * <p><b>业务场景：</b>下单成功后要记日志、发短信、加积分。
 * 这些操作和"下单成功"这个结果无关，不该让用户等着，
 * 所以丢到异步线程池里去做，接口响应时间立刻下降。</p>
 *
 * <p><b>代价（必须讲清楚）：</b></p>
 * <ul>
 *     <li>异步方法抛异常，主线程<b>感知不到</b>，需要自己 try-catch + 告警，
 *         否则任务静默丢失</li>
 *     <li>异步任务<b>不在主线程事务里</b>：主线程事务回滚了，异步任务照样执行</li>
 *     <li>服务重启时队列里的任务可能丢失，重要任务要走 MQ + 落库重试</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAsyncService {

    private final OrderLogMapper orderLogMapper;

    /**
     * 异步写入订单日志。
     *
     * <p><b>@Async 生效的三个前提（缺一不可）：</b></p>
     * <ol>
     *     <li>启动类或配置类上有 {@code @EnableAsync}</li>
     *     <li>方法必须是 <b>public</b>，且只能通过<b>注入的 Bean</b> 调用（同类自调用无效）</li>
     *     <li>返回值只能是 {@code void} 或 {@code Future}，返回其他类型拿不到异步结果</li>
     * </ol>
     *
     * <p>{@code @Async("mallExecutor")} 指定线程池名。不写名字的话会用 Spring 默认的
     * {@code SimpleAsyncTaskExecutor} —— 每个任务新建一个线程，高并发下线程数爆炸。</p>
     */
    @Async("mallExecutor")
    public void asyncRecordLog(String orderNo, String content) {
        try {
            // 打印线程名，课堂上和 http-nio-8080-exec-x 对比，一眼看出异步是否生效
            log.info("【异步线程】开始写订单日志 | orderNo={} | thread={}",
                    orderNo, Thread.currentThread().getName());

            OrderLog orderLog = new OrderLog();
            orderLog.setOrderNo(orderNo);
            orderLog.setContent(content);
            orderLog.setThreadName(Thread.currentThread().getName());
            orderLogMapper.insert(orderLog);

            log.info("【异步线程】订单日志写入完成 | orderNo={}", orderNo);
        } catch (Exception e) {
            // ★ 异步方法一定要自己兜住异常：否则异常只在日志里一闪而过，
            //   主流程完全不知道日志没写成功。
            log.error("【异步线程】写订单日志失败 | orderNo={}", orderNo, e);
        }
    }

    /**
     * 【错误示范】同类内部自调用 —— @Async 直接失效。
     *
     * <p>这里 {@code this.asyncRecordLog(...)} 走的是<b>原始对象</b>的引用，
     * 绕过了 Spring 生成的代理对象，所以 @Async 完全不起作用，
     * 代码会<b>同步执行</b>（日志线程名还是 http-nio-...）。</p>
     *
     * <p><b>正确做法有两种：</b></p>
     * <ol>
     *     <li>把异步方法挪到另一个 Bean 里（比如本类拆成 OrderLogAsyncService）</li>
     *     <li>自己注入自己：{@code @Autowired private OrderAsyncService self; }
     *         然后调 {@code self.asyncRecordLog(...)}（Spring 允许，但要注意循环依赖）</li>
     * </ol>
     *
     * <p><b>⚠️ 同一个坑还有两个"兄弟"：</b>{@code @Transactional} 自调用失效、
     * {@code @Cacheable} 自调用失效。三个注解失效的原理<b>完全一样</b>，
     * 都是"动态代理被绕过"。记住这一条，面试能答一大片题。</p>
     */
    public void selfInvocationDemo(String orderNo) {
        log.info("【错误示范】即将通过 this 调用 @Async 方法 | thread={}", Thread.currentThread().getName());
        this.asyncRecordLog(orderNo, "同类内部调用 @Async —— 本应异步，实际同步");
        log.info("【错误示范】this 调用返回 | thread={}（线程名没变说明异步失效）",
                Thread.currentThread().getName());
    }
}
