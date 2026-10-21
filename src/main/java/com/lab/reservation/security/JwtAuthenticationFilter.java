package com.lab.reservation.security;

import com.lab.reservation.config.JwtProperties;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.service.PermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.hutool.core.util.StrUtil;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * JWT 认证过滤器
 *
 * <p>对每个请求：解析 Authorization 头中的 Token → 取用户ID → 从 Redis 加载
 * 已缓存的 {@link LoginUser}（未命中则回查数据库）→ 写入 SecurityContext，完成认证。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getPrefix();

        // 无 Token 或前缀不符：不强制拦截，放行由 Security 决定是否 401
        if (StrUtil.isNotBlank(header) && header.startsWith(prefix)) {
            String token = header.substring(prefix.length());
            try {
                Long userId = jwtUtil.parseUserId(token);
                LoginUser loginUser = loadLoginUser(userId);
                // 密码变更后写入的吊销标记：该账号已签发的旧令牌一律失效，须重新登录
                boolean revoked = Boolean.TRUE.equals(
                        redisTemplate.hasKey(RedisKeyConstants.revokedKey(userId)));

                if (!revoked && loginUser != null && loginUser.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    // 写入安全上下文，后续 @PreAuthorize / SecurityUtil 均可读取
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 非法/过期：保持未认证状态，交由 AuthenticationEntryPoint 返回 401
                log.debug("JWT 认证失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 加载登录用户：优先取 Redis 缓存，未命中回查数据库并回填缓存
     */
    private LoginUser loadLoginUser(Long userId) {
        String key = RedisKeyConstants.userKey(userId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof LoginUser loginUser) {
            return loginUser;
        }
        LoginUser loginUser = permissionService.loadLoginUserByUserId(userId);
        if (loginUser != null) {
            redisTemplate.opsForValue().set(
                    key, loginUser, jwtProperties.getExpiration(), TimeUnit.MILLISECONDS);
        }
        return loginUser;
    }
}