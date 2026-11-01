package com.lab.reservation.aop;

import cn.hutool.core.util.StrUtil;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 请求防护切面：接口限流 + 提交幂等
 *
 * <p style="color:#c00"><b>答辩亮点：</b>与 {@link OperationLogAspect} 同属 AOP 横切关注点，
 * 但职责是「保护入口」而不是「记录结果」。</p>
 *
 * <ul>
 *   <li><b>限流：</b>Redis INCR + 首次计数设置过期时间，实现固定窗口计数；
 *       计数主体优先取登录用户ID，未登录退化到 IP，避免同一账号多端连点绕过额度。</li>
 *   <li><b>幂等：</b>按 {@code X-Idempotency-Key} 做 SETNX 占位，同一令牌只放行一次。</li>
 *   <li><b>降级：</b>Redis 异常时不阻断业务（fail-open），并打警告日志；
 *       真正的数据一致性仍由状态机、行锁与唯一约束兜底。</li>
 * </ul>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequestGuardAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /** 幂等令牌请求头名称 */
    private static final String IDEMPOTENT_HEADER = "X-Idempotency-Key";

    /**
     * 限流：固定窗口内超过阈值直接拒绝
     */
    @Around("@annotation(rateLimit)")
    public Object aroundRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String biz = StrUtil.blankToDefault(rateLimit.key(), joinPoint.getSignature().getName());
        String key = RedisKeyConstants.rateLimitKey(biz, subject());
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(rateLimit.seconds()));
            }
            if (count != null && count > rateLimit.limit()) {
                log.warn("[限流] biz={}, key={}, count={}", biz, key, count);
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                        String.format("%d 秒内最多操作 %d 次，请稍后再试",
                                rateLimit.seconds(), rateLimit.limit()));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时放行，避免缓存故障放大成业务不可用
            log.warn("[限流] Redis 异常，已降级放行：{}", e.getMessage());
        }
        return joinPoint.proceed();
    }

    /**
     * 幂等：同一客户端令牌只允许成功进入一次
     */
    @Around("@annotation(idempotent)")
    public Object aroundIdempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String token = currentRequest() == null
                ? null : currentRequest().getHeader(IDEMPOTENT_HEADER);
        if (StrUtil.isBlank(token)) {
            return joinPoint.proceed();
        }
        String biz = StrUtil.blankToDefault(idempotent.key(), joinPoint.getSignature().getName());
        String key = RedisKeyConstants.idempotentKey(biz, token.trim());
        try {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.seconds()));
            if (Boolean.FALSE.equals(first)) {
                log.warn("[幂等] 重复提交被拦截 biz={}, token={}", biz, token);
                throw new BusinessException(ResultCode.DUPLICATE_REQUEST);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[幂等] Redis 异常，已降级放行：{}", e.getMessage());
        }
        return joinPoint.proceed();
    }

    /**
     * 限流计数主体：登录用户优先，未登录用客户端 IP
     */
    private String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return "u" + user.getUserId();
        }
        HttpServletRequest request = currentRequest();
        return "ip" + (request == null ? "unknown" : request.getRemoteAddr());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
