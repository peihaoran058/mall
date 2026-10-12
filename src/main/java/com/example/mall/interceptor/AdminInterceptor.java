package com.example.mall.interceptor;

import com.example.mall.common.Constants;
import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {


    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String role = request.getAttribute(Constants.REQ_ROLE).toString();
        if (Constants.ROLE_ADMIN.equals(role)){
            return true;
        }
        log.warn("非管理员访问后台接口被拦截 | uri={} | role={}", request.getRequestURI(), role);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(Result.fail(ResultCode.FORBIDDEN, "该接口仅管理员可访问")));
            writer.flush();
        }
        return false;
    }
}
