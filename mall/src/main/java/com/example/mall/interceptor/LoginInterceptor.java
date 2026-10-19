package com.example.mall.interceptor;

import com.example.mall.common.Constants;
import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import com.example.mall.entity.Admin;
import com.example.mall.entity.SysUser;
import com.example.mall.mapper.AdminMapper;
import com.example.mall.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器。
 *
 * <p>支持两种登录态，用户端和管理后台共用同一个拦截器：</p>
 * <ol>
 *     <li><b>Redis Token</b>（推荐）：{@code token} 请求头 → Redis 里查登录信息，天然支持集群</li>
 *     <li><b>HttpSession</b>（第4课时讲的方案）：{@code request.getSession(false)} 取</li>
 * </ol>
 *
 * <p><b>课上重点：session 的缺点（这也是为什么后面要换成 Redis）</b></p>
 * <ul>
 *     <li>服务部署两台机器，session 存在各自内存里，用户在 A 机器登录、请求打到 B 机器就掉线（需要额外做 session 共享或粘性会话）</li>
 *     <li>session 放在应用内存里，重启即丢失；用户量大了内存压力大</li>
 *     <li>前后端分离 + 小程序/APP 场景，Cookie 不可靠，用 Token 更合适</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;
    private final AdminMapper adminMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 静态资源、非 Controller 方法直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 2. 跨域预检请求放行（OPTIONS 不带业务参数）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 3. 优先用 Token 鉴权
        // 获取前端传入的token
        String token = request.getHeader(Constants.HEADER_TOKEN);

        if(StringUtils.hasText(token)){
            // 获取登录的普通用户信息
            SysUser user = (SysUser)redisTemplate.opsForValue().get(Constants.REDIS_LOGIN_USER + token);
            if (user != null){
                SysUser sysUser = sysUserMapper.selectById(user.getId());
                if (user.getPasswordVersion().equals(sysUser.getPasswordVersion())){
                    markUser(request,sysUser.getId());
                    return true;
                }
            }

            // 获取登录的管理员信息
            Admin admin1 = (Admin)redisTemplate.opsForValue().get(Constants.REDIS_LOGIN_ADMIN + token);
            if (admin1 != null){
                Admin admin = adminMapper.selectById(admin1.getId());
                if (admin1.getPasswordVersion().equals(admin.getPasswordVersion())){
                    markAdmin(request,admin.getId());
                    return true;
                }
            }


        }

        // 4. 都没登录：返回未授权
        writeUnauthorized(response);
        return false;
    }


    private void markUser(HttpServletRequest request, Long userId) {
        request.setAttribute(Constants.REQ_USER_ID, userId);
        request.setAttribute(Constants.REQ_ROLE, Constants.ROLE_USER);
    }

    private void markAdmin(HttpServletRequest request, Long adminId) {
        request.setAttribute(Constants.REQ_ADMIN_ID, adminId);
        request.setAttribute(Constants.REQ_ROLE, Constants.ROLE_ADMIN);
    }

    /**
     * 拦截器里直接返回 JSON 时，最容易踩的坑是中文乱码 + 前端拿不到 CORS 头。
     * 这里统一用 UTF-8 写，并且 CORS 交给 CorsFilter 提前加，不在这里处理。
     */
    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ResultCode.UNAUTHORIZED);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(result));
            writer.flush();
        }
    }
}
