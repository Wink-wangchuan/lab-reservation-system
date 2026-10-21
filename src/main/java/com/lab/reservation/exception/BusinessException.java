package com.lab.reservation.exception;

import com.lab.reservation.common.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 *
 * <p>在 Service 层遇到业务规则被违反时主动抛出（如：预约冲突、设备故障锁定、
 * 状态机非法流转），由 {@link GlobalExceptionHandler} 统一捕获转换为 JSON 返回。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final Integer code;

    /** 扩展错误信息（覆盖枚举默认 message） */
    private final String detail;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.detail = message;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.detail = resultCode.getMessage();
    }

    public BusinessException(ResultCode resultCode, String detail) {
        super(detail);
        this.code = resultCode.getCode();
        this.detail = detail;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.detail = message;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.detail = message;
    }
}