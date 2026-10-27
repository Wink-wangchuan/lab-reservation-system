package com.lab.reservation.exception;

import com.lab.reservation.common.Result;
import com.lab.reservation.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一拦截 Controller 抛出的各类异常，转换为 {@link Result} JSON 返回，
 * 保证"异常不裸奔"——前端永远拿到结构一致的响应，答辩亮点之一。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@RequestBody + @Valid）
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        log.warn("[参数校验异常] {}", msg);
        return Result.error(ResultCode.PARAM_ERROR, msg);
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ResultCode.PARAM_ERROR, "缺少参数：" + e.getParameterName());
    }

    /**
     * 路径变量 / 查询参数类型不匹配（如 /api/checkin/undefined 这类非法 ID）
     *
     * <p>属于调用方参数错误，必须返回 40000 而不是兜底成 50050 系统异常，
     * 同时避免把类型转换细节暴露给前端。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[参数类型错误] name={}, value={}", e.getName(), e.getValue());
        return Result.error(ResultCode.PARAM_ERROR, "参数格式不正确：" + e.getName());
    }

    /**
     * 请求体无法解析：JSON 语法错误、字段类型不匹配、请求体缺失等
     * <p>统一按参数错误返回，避免这类客户端问题被兜底成 50050 系统异常</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[请求体解析失败] {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR, "请求体格式不正确，请检查提交内容");
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(40500, "请求方法不支持：" + e.getMethod());
    }

    /**
     * 文件上传超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(ResultCode.PARAM_ERROR, "上传文件超出大小限制");
    }

    /**
     * 404 未找到处理器 / 资源
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Result<Void> handleNotFound(Exception e) {
        return Result.error(40400, "请求的资源不存在");
    }

    /**
     * 数据库唯一键冲突（直连 JDBC 场景）
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result<Void> handleSqlConflict(SQLIntegrityConstraintViolationException e) {
        log.warn("[数据库约束冲突] {}", e.getMessage());
        return Result.error(ResultCode.DATA_EXISTS, "数据已存在，请勿重复提交");
    }

    /**
     * 数据库唯一键冲突（MyBatis 场景）
     *
     * <p>MyBatis-Spring 会把 SQL 异常转译为 Spring 的 DataAccessException 体系
     * （唯一键冲突 → {@link DuplicateKeyException}），因此上面针对原生
     * SQLIntegrityConstraintViolationException 的处理器不会命中，
     * 若缺少本处理器，唯一键冲突会被兜底成「50050 系统内部异常」。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("[唯一键冲突] {}", e.getMessage());
        return Result.error(ResultCode.DATA_EXISTS, "数据已存在，请勿重复提交");
    }

    /**
     * Spring Security @PreAuthorize 权限拦截（方法级）
     * <p>SecurityFilterChain 级 AccessDeniedHandler 已配置，但方法级抛出时会进入 ControllerAdvice</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("[权限拦截] 无权限访问", e);
        return Result.error(ResultCode.FORBIDDEN);
    }

    /**
     * 兜底异常：捕获所有未处理异常，防止堆栈信息泄漏给前端
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常] ", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}