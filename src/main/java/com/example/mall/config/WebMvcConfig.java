package com.example.mall.config;


import com.example.mall.interceptor.AdminInterceptor;
import com.example.mall.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * SpringMVC 基础配置（笔记 4.4.1 拦截器）。
 *
 * <p><b>课上重点一：注册顺序 = 执行顺序。</b><br>
 * 这里先注册登录拦截器，再注册管理员拦截器。
 * 如果顺序反了，{@code AdminInterceptor} 拿不到登录拦截器放进去的 role，
 * 后台所有接口都会返回 403 —— 而这种问题启动时不报错，只能靠测试发现。</p>
 *
 * <p><b>课上重点二：excludePathPatterns 写成 {@code "/api/user/*"} 会踩什么坑？</b><br>
 * 它只匹配一级路径，{@code /api/user/info} 能放行，
 * {@code /api/user/info/detail} 就拦不住了。这种"以为放行了其实没放行
 * （或者反过来）"的问题，在真实项目里非常容易造成越权漏洞，
 * 必须用 Apifox 逐个接口验证。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final MallProperties mallProperties;

    /**
     * 注册拦截器。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：登录校验，只拦 /api/**，放行登录注册、匿名可看的商品分类、演示接口
        registry.addInterceptor(loginInterceptor)
                .order(1)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 认证相关
                        "/api/user/register",
                        "/api/user/login",
                        "/api/admin/login",
                        // 匿名可浏览：分类树 + 全部商品接口（含 /{id}/cache-test 缓存演示）
                        "/api/category/tree",
                        "/api/product/**",
                        // 课堂错误演示接口（不需要登录，方便课堂上直接点）
                        "/api/demo/**",
                        // 错误页、静态资源
                        "/error",
                        "/favicon.ico",
                        "/api/file/upload"
                );

        // 第二层：管理员角色校验，只拦后台接口
        registry.addInterceptor(adminInterceptor)
                .order(2)
                .addPathPatterns("/api/admin/**")
                // ★ 必须放行后台登录接口本身！
                //   如果漏了这一行，/api/admin/login 会被 AdminInterceptor 拦下，
                //   此时请求里还没有 role（用户压根还没登录），直接返回 403
                //   —— 结果就是「管理员永远登录不进去」，而且报错信息是
                //   "该接口仅管理员可访问"，非常容易误以为是密码错了。
                //   这类问题是"拦截器写对了范围、却忘了给入口开洞"的典型，课堂必讲。
                .excludePathPatterns("/api/admin/login");
    }

    /**
     * 把本地上传目录映射成可访问的静态资源路径。
     * 注意 file: 后面必须跟**绝对路径**，并且 Windows 下要用正斜杠或双反斜杠。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = mallProperties.getUpload().getPath();
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("[上传目录] " + dir.getAbsolutePath() + (created ? " 创建成功" : " 已存在或创建失败"));
        }
        registry.addResourceHandler(mallProperties.getUpload().getUrlPrefix() + "**")
                .addResourceLocations("file:" + dir.getAbsolutePath() + File.separator);
    }
}
