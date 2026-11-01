package com.lab.reservation.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路标识过滤器
 *
 * <p style="color:#c00"><b>答辩亮点：</b>为每个请求生成一个 traceId 放进 MDC，
 * 日志格式统一输出 {@code [traceId]}，同一次请求在日志里的所有行可以串起来查；
 * 同时回写响应头 {@code X-Trace-Id}，前端报错时可把该 ID 提供给运维定位。</p>
 *
 * <p>使用 OncePerRequestFilter 保证异步派发不会重复生成；线程池复用线程时
 * 必须在 finally 中清理 MDC，否则 traceId 会串到下一个请求。</p>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 中的键名，与 logback-spring.xml 的 %X{traceId} 对应 */
    public static final String TRACE_ID = "traceId";

    /** 响应头名称 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
