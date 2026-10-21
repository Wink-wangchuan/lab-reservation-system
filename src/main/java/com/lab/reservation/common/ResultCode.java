package com.lab.reservation.common;

import lombok.Getter;

/**
 * 统一返回状态码枚举
 *
 * <p>集中管理所有接口返回码，避免魔法数字散落各处。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Getter
public enum ResultCode {

    /* ==================== 通用 ==================== */
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    /* ==================== 参数 ==================== */
    PARAM_ERROR(40000, "参数校验失败"),

    /* ==================== 认证 / 鉴权 ==================== */
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    LOGIN_FAILED(40101, "账号或密码错误"),
    ACCOUNT_DISABLED(40102, "账号已被禁用"),
    ACCOUNT_PENDING(40103, "账号待管理员审核，暂无法登录"),
    CAPTCHA_INVALID(40104, "验证码错误"),
    CAPTCHA_EXPIRED(40105, "验证码已失效，请重新获取"),
    CAPTCHA_TOO_FREQUENT(40106, "请求过于频繁，请稍后再试"),
    FORBIDDEN(40300, "无权限访问该资源"),

    /* ==================== 业务通用 ==================== */
    NOT_FOUND(40400, "数据不存在"),
    DATA_EXISTS(40401, "数据已存在"),
    BUSINESS_ERROR(50000, "业务处理失败"),
    SYSTEM_ERROR(50050, "系统内部异常"),
    CONCURRENT_MODIFIED(50001, "数据已被他人修改，请刷新后重试"),

    /* ==================== 预约相关 ==================== */
    RESERVATION_CONFLICT(50100, "预约时间冲突"),
    RESERVATION_LOCKED(50101, "实验室设备故障，禁止预约"),
    RESERVATION_NOT_ALLOW(50102, "该时段不在开放范围或已停止预约"),
    RESERVATION_STATUS_ERROR(50103, "预约状态不允许当前操作"),
    RESERVATION_OVER_CAPACITY(50104, "预约人数超过时段上限"),

    /* ==================== 设备相关 ==================== */
    DEVICE_NOT_FOUND(50200, "设备不存在"),
    DEVICE_REPAIR_LOCKED(50201, "设备正在维修，所属实验室已锁定"),

    /* ==================== 报表 / 导出 ==================== */
    EXPORT_ERROR(50300, "导出失败"),

    /* ==================== 签到核销 ==================== */
    CHECKIN_CODE_INVALID(50400, "核销码无效"),
    CHECKIN_NOT_IN_WINDOW(50401, "当前不在可签到时段"),
    CHECKIN_EXPIRED(50402, "已超出预约时段，签到失败"),

    /* ==================== AI 助手 ==================== */
    AI_NOT_CONFIGURED(50500, "AI 助手未配置，请联系管理员"),
    AI_TOO_FREQUENT(50501, "提问过于频繁，请稍后再试"),
    AI_UPSTREAM_ERROR(50502, "AI 服务暂时不可用，请稍后重试"),
    AI_BUSY(50503, "上一条回答还在生成中，请稍候");

    /** 状态码 */
    private final int code;

    /** 默认提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}