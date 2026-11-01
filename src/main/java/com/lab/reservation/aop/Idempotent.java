package com.lab.reservation.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 提交幂等注解
 *
 * <p style="color:#c00"><b>答辩亮点：</b>前端每次打开表单时生成一个令牌，
 * 通过请求头 {@code X-Idempotency-Key} 携带；服务端用 Redis SETNX 占位，
 * 同一令牌重复到达（双击、网络重试、浏览器回退重发）直接返回 42901，
 * 避免生成两条内容相同的业务单据。</p>
 *
 * <p>未携带令牌时不做拦截（保持接口可用性），真正的重复仍由业务唯一性校验兜底。</p>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等业务标识，默认取方法名 */
    String key() default "";

    /** 令牌保留时长（秒），需覆盖用户填表 + 网络重试的最长时间 */
    int seconds() default 600;
}
