package com.lab.reservation.config;

import com.lab.reservation.security.JwtAuthenticationFilter;
import com.lab.reservation.security.RestAccessDeniedHandler;
import com.lab.reservation.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置（正式版）
 *
 * <p style="color:#c00"><b>答辩亮点：</b>无状态 JWT 认证 + RBAC 权限校验。</p>
 * <ul>
 *   <li>登录态存于无状态 Token，服务端不存 Session（STATELESS）</li>
 *   <li>{@code @EnableMethodSecurity} + {@code @PreAuthorize} 实现细粒度权限控制</li>
 *   <li>401/403 统一返回 JSON 结构</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize 方法级权限校验（RBAC 落点）
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * 允许跨域的来源，逗号分隔
     *
     * <p>必须是白名单：配合 {@code allowCredentials=true} 时若允许 {@code *}，
     * 任意站点都能以用户身份跨域调用接口（CSRF/CORS 配置错误）。</p>
     */
    @Value("${cors.allowed-origins:http://localhost:8081,http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 密码加密器（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())                    // 前后端分离，禁用 CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态
                // 未认证 / 无权限统一 JSON 返回
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 异步派发放行（SSE / DeferredResult 必需）：
                        // 流式请求结束时容器会做一次 ASYNC 二次派发，此次派发同样会经过安全过滤器链，
                        // 但 SecurityContext 已随原请求线程结束而清空，若不放行会被 AuthorizationFilter
                        // 判为匿名访问而拒绝，表现为响应已提交却抛出 Access Denied，
                        // 浏览器侧看到 net::ERR_INCOMPLETE_CHUNKED_ENCODING。
                        // 原请求在首次派发时已通过认证与 @PreAuthorize 校验，此处放行不会绕过鉴权。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 白名单：接口文档、匿名认证入口、放行 OPTIONS 预检
                        // 注意：这里必须逐条列出，不可写成 /api/auth/**，
                        // 否则 /api/auth/info、/api/auth/logout 会从 401 变成 403，
                        // 破坏前端「401 → 清除令牌跳登录」的处理链路
                        .requestMatchers(
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui/**",
                                "/favicon.ico",
                                "/error",
                                "/api/auth/login",
                                "/api/auth/captcha",
                                "/api/auth/login-by-code",
                                "/api/auth/register",
                                "/api/auth/reset-password"
                        ).permitAll()
                        // 其余请求全部需要认证
                        .anyRequest().authenticated())
                // JWT 过滤器置于用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 跨域配置（供前端分离项目访问）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 白名单来源：由环境变量 CORS_ALLOWED_ORIGINS 注入，禁止使用通配符
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 导出 Excel 时前端需要读取 Content-Disposition 取得文件名，跨域下必须显式暴露
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}