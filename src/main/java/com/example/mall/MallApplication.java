package com.example.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 电商项目启动类。
 *
 * <p>三个注解是课程里的"开关"，少一个对应的功能就会静默失效（这是最高频的踩坑点）：</p>
 * <ul>
 *     <li>{@code @MapperScan} —— 不写它，Mapper 接口不会生成代理，启动直接报 NoSuchBeanDefinition</li>
 *     <li>{@code @EnableCaching} —— 不写它，{@code @Cacheable} 只是一个普通注解，不会走缓存，也不报错</li>
 *     <li>{@code @EnableAsync} —— 不写它，{@code @Async} 会变成同步执行，不报错但失去异步效果</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.example.mall.mapper")
@EnableCaching
@EnableAsync
public class MallApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
        System.out.println("\n=========================================================");
        System.out.println("  电商项目启动成功");
        System.out.println("  接口文档集合: postman/mall.postman_collection.json");
        System.out.println("  健康检查:      http://localhost:8080/actuator/health");
        System.out.println("=========================================================\n");
    }
}
