package com.example.mall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 自定义异步线程池（第15课时）。
 *
 * <p><b>课上重点：为什么不直接用 Spring 默认线程池？</b><br>
 * 不指定线程池时，{@code @Async} 会用 {@code SimpleAsyncTaskExecutor}，
 * 它的特点是**每个任务都新起一个线程、不复用**，高并发下线程数会失控。
 * 这个坑在压测时才会暴露，必须自己定义。</p>
 *
 * <p>关于拒绝策略：{@code CallerRunsPolicy} 表示队列满了就让调用者线程自己跑。
 * 它的好处是"不会丢任务"，代价是**会把业务线程拖慢**（相当于降级成同步）。
 * 订单这种不能丢的场景可以这么选，日志这种可丢的场景用 AbortPolicy 更好。</p>
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Bean("mallExecutor")
    public Executor mallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：常驻，即使空闲也不销毁
        executor.setCorePoolSize(8);
        // 最大线程数：核心满了且队列也满了才会扩容到这里
        executor.setMaxPoolSize(16);
        // 队列容量：核心线程都在忙时，任务先排队
        executor.setQueueCapacity(200);
        // 空闲线程存活时间
        executor.setKeepAliveSeconds(60);
        // 线程名前缀：排查问题时一眼看出是哪个线程池
        executor.setThreadNamePrefix("mall-async-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等正在执行的任务跑完再退出
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("初始化异步线程池 mallExecutor | core={} max={} queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
