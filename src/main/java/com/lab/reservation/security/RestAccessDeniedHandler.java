package com.lab.reservation.security;

import cn.hutool.json.JSONUtil;
import com.lab.reservation.common.Result;
import com.lab.reservation.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 权限不足处理器（403）
 *
 * <p>已登录但无 @PreAuthorize 所需权限时返回统一 JSON 结构。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[403 无权限] uri={}, 用户无访问权限",
                request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                JSONUtil.toJsonStr(Result.error(ResultCode.FORBIDDEN)));
    }
}