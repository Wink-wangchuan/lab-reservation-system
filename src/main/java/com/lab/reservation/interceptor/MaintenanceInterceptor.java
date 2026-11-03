package com.lab.reservation.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.reservation.common.Result;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;

/**
 * 维护模式拦截器
 *
 * <p>维护模式开启后，除登录/门户状态/健康检查/客户端更新外，
 * 普通用户的业务接口统一返回维护提示；管理员（具备 system:config:manage）
 * 不受限制，否则开启维护模式后就没人能再关掉它。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    private final SysConfigService configService;
    private final ObjectMapper objectMapper;

    /** 维护期间仍需放行的接口前缀 */
    private static final String[] ALLOWED_PREFIXES = {
            "/api/auth/", "/api/portal/", "/api/health", "/api/app/"
    };

    private static final String ADMIN_PERM = "system:config:manage";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!configService.maintenanceMode()) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String prefix : ALLOWED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        if (SecurityUtil.hasPermission(ADMIN_PERM)) {
            return true;
        }
        // 业务码而非 HTTP 503：前端响应拦截器统一按 code≠200 弹出提示
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(50300, configService.maintenanceNotice())));
        return false;
    }
}
