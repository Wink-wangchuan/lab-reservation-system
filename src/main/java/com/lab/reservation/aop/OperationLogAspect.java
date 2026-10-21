package com.lab.reservation.aop;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import com.lab.reservation.entity.SysOperLog;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面
 *
 * <p style="color:#c00"><b>答辩亮点：AOP 全局操作日志</b></p>
 * 用{@code @around}思想拆分为 before/afterReturning/afterThrowing 三阶段：
 * <ul>
 *   <li>before：记录进入时间与 IP</li>
 *   <li>afterReturning：记录返回结果 JSON（截断），状态=成功</li>
 *   <li>afterThrowing：记录异常栈首行，状态=失败</li>
 * </ul>
 * 统一通过 {@link OperLogService#saveLog} 异步落库，不阻塞主业务。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /** 请求参数/返回结果最大记录长度，防止超长拖垮日志表 */
    private static final int MAX_LEN = 2000;

    private final OperLogService operLogService;

    /** 以线程局部保存每次请求的耗时计时起点 */
    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    /** 切入点：标注了 @OperationLog 的 Controller 方法 */
    @Pointcut("@annotation(com.lab.reservation.aop.OperationLog)")
    public void logPointcut() {
    }

    @Before("logPointcut()")
    public void doBefore() {
        START_TIME.set(System.currentTimeMillis());
    }

    @AfterReturning(pointcut = "logPointcut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) {
        handleLog(joinPoint, null, result);
    }

    @AfterThrowing(pointcut = "logPointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Throwable e) {
        handleLog(joinPoint, e, null);
    }

    /**
     * 统一组装并异步保存日志
     */
    private void handleLog(JoinPoint joinPoint, Throwable e, Object result) {
        try {
            // 1. 读取注解元信息
            OperationLog annotation = getAnnotation(joinPoint);
            if (annotation == null) {
                return;
            }
            // 2. 请求上下文
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            SysOperLog operLog = new SysOperLog();
            operLog.setTitle(annotation.module());
            operLog.setBusinessType(annotation.businessType());
            operLog.setMethod(joinPoint.getSignature().toShortString());
            operLog.setOperTime(LocalDateTime.now());

            // 3. 操作人（SecurityContext 中取，未登录则为匿名）
            SetUserNameFromSecurity(operLog);

            // 4. 请求信息（IP / URL / 方法 / 参数）
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operLog.setRequestMethod(request.getMethod());
                operLog.setOperUrl(request.getRequestURI());
                operLog.setOperIp(JakartaServletUtil.getClientIP(request));
                operLog.setOperParam(StrUtil.maxLength(
                        JSONUtil.toJsonStr(cleanArgs(joinPoint.getArgs())), MAX_LEN));
            }

            // 5. 结果/异常 + 耗时 + 状态
            Long start = START_TIME.get();
            operLog.setCostTime(start == null ? 0L : System.currentTimeMillis() - start);
            if (e != null) {
                operLog.setStatus(0);
                operLog.setErrorMsg(StrUtil.maxLength(
                        e.getClass().getSimpleName() + ": " + e.getMessage(), MAX_LEN));
            } else {
                operLog.setStatus(1);
                if (result != null) {
                    operLog.setJsonResult(StrUtil.maxLength(
                            JSONUtil.toJsonStr(result), MAX_LEN));
                }
            }

            operLogService.saveLog(operLog);
        } catch (Exception ex) {
            // 日志记录失败不影响主业务
            log.error("[操作日志] 记录失败: {}", ex.getMessage());
        } finally {
            START_TIME.remove();
        }
    }

    private OperationLog getAnnotation(JoinPoint joinPoint) {
        var method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        return method.getAnnotation(OperationLog.class);
    }

    /**
     * 兼容安全上下文：可能为 LoginUser（JWT）或匿名 AnonymousAuthenticationToken
     */
    private void SetUserNameFromSecurity(SysOperLog operLog) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            operLog.setOperName("匿名");
            return;
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser) {
            operLog.setOperName(loginUser.getRealName() + "(" + loginUser.getUsername() + ")");
        } else {
            operLog.setOperName(String.valueOf(authentication.getName()));
        }
    }

    /**
     * 过滤不可序列化参数（HttpServletRequest/Response/MultipartFile）
     */
    private Object[] cleanArgs(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest
                        || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile)
                        && arg != null)
                .toArray();
    }
}