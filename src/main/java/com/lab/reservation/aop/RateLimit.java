package com.lab.reservation.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * <p style="color:#c00"><b>答辩亮点：</b>基于 Redis 计数器的轻量限流，
 * 由 {@link RequestGuardAspect} 在进入业务方法前拦截，超限直接返回 42900。</p>
 *
 * <p>计数主体优先取登录用户，未登录（如登录接口）退化到客户端 IP，
 * 因此同一账号在多个浏览器连点也只算一份额度。</p>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的最大请求次数 */
    int limit() default 10;

    /** 时间窗口长度（秒） */
    int seconds() default 60;

    /** 限流业务标识，默认取方法名（同类方法可用显式名字聚合额度） */
    String key() default "";
}
