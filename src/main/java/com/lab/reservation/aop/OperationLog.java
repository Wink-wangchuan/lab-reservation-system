package com.lab.reservation.aop;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p style="color:#c00"><b>答辩亮点：AOP + 自定义注解实现全局操作日志</b></p>
 * 标注在 Controller 方法上，由 {@link OperationLogAspect} 统一拦截：
 * 记录操作人、请求参数、返回结果、耗时、异常信息并落库到 {@code sys_oper_log}。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 模块标题 */
    String module();

    /** 业务类型：insert/update/delete/query */
    String businessType();

    /** 操作描述 */
    String description() default "";
}