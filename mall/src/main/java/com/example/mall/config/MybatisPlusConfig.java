package com.example.mall.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（笔记 5.3）。
 *
 * <p><b>课上重点：</b>分页插件忘了配会怎么样？<br>
 * 不会报错！{@code page()} 方法照样返回数据，但返回的是**全部数据**，
 * total 也等于当前页条数。这是最容易被忽略的坑——本地数据少看不出来，
 * 上线数据一多直接 OOM。</p>
 *
 * <p>另外注意：MyBatis-Plus 3.5.9 之后分页插件被拆到
 * {@code mybatis-plus-jsqlparser} 依赖里，本项目用 3.5.3.1，内置不用额外引。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：必须指定数据库类型，否则 count 语句可能拼错
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数保护：防止前端传 size=100000 把库拖死
        pagination.setMaxLimit(500L);
        // 请求页码超过总页数时不回到首页，直接返回空列表（更符合前端预期）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
