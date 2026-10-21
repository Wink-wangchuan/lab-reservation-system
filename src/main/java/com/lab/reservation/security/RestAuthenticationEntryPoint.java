package com.lab.reservation.security;

import cn.hutool.json.JSONUtil;
import com.lab.reservation.common.Result;
import com.lab.reservation.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证处理器（401）
 *
 * <p>当未登录或 Token 失效访问受保护资源时，返回统一 JSON 结构而非常规登录页，
 * 保证前后端分离下前端能识别 401 并跳转登录。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[401 未认证] uri={}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED)));
    }
}