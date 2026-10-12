package com.example.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域配置（笔记 4.7）。
 *
 * <p><b>课上重点：为什么用 Filter 而不是 WebMvcConfigurer#addCorsMappings？</b><br>
 * 因为 {@code addCorsMappings} 属于 SpringMVC 层，只在进入 DispatcherServlet 后生效。
 * 一旦请求被 Filter / 拦截器提前拦下（比如返回 401 的未登录请求），
 * 响应里就没有 CORS 头，浏览器只会报"跨域错误"，真实原因（未登录）反而被掩盖。
 * 用 Filter 提前写头，任何响应都能带上 CORS 头。</p>
 *
 * <p>生产环境不要用 {@code "*"}，要写明确的前端域名白名单。</p>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带 Cookie（用 session 方案时必须开，且 allowedOrigins 不能是 *）
        config.setAllowCredentials(true);
        // 用 Pattern 代替 Origins，可以在 allowCredentials=true 的同时支持通配
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // 暴露给前端 JS 能读到的响应头（比如自己返回的 token）
        config.addExposedHeader("token");
        config.addExposedHeader("Content-Disposition");
        // 预检请求缓存 1 小时，减少 OPTIONS 请求
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
