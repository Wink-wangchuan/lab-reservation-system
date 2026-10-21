package com.lab.reservation.enums;

import lombok.Getter;

import java.util.Set;

/**
 * 预约签到核销状态机
 *
 * <p style="color:#c00"><b>答辩亮点：预约业务闭环的最后一段</b></p>
 * 预约审批通过后自动生成签到凭证（核销码），到场后由管理员/教师核销，
 * 使用结束签退即完成整条链路：提交 → 审批 → 签到 → 签退完成。
 *
 * <pre>
 * 合法流转：
 *   SIGN_IN_PENDING 待签到 -> CHECKED_IN(核销签到) | EXPIRED(超时未签到) | CANCELLED(预约取消)
 *   CHECKED_IN      已签到 -> COMPLETED(签退完成) | CANCELLED(预约取消)
 *   EXPIRED / COMPLETED / CANCELLED 为终态
 * </pre>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Getter
public enum CheckinStatus {

    /** 待签到（审批通过后已生成核销码） */
    SIGN_IN_PENDING("待签到"),
    /** 已签到 */
    CHECKED_IN("已签到"),
    /** 已完成（已签退） */
    COMPLETED("已完成"),
    /** 已过期（超出预约时段仍未签到） */
    EXPIRED("已过期"),
    /** 已取消 */
    CANCELLED("已取消");

    /** 中文描述（用于展示） */
    private final String desc;

    CheckinStatus(String desc) {
        this.desc = desc;
    }

    /**
     * 判定状态机是否允许 from -> to 合法流转
     */
    public static boolean canTransition(CheckinStatus from, CheckinStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case SIGN_IN_PENDING -> Set.of(CHECKED_IN, EXPIRED, CANCELLED).contains(to);
            case CHECKED_IN -> Set.of(COMPLETED, CANCELLED).contains(to);
            default -> false; // EXPIRED / COMPLETED / CANCELLED 为终态
        };
    }

    /**
     * 是否为终态（不可再流转）
     */
    public boolean isTerminal() {
        return this == EXPIRED || this == COMPLETED || this == CANCELLED;
    }

    /**
     * 字符串转枚举，非法返回 null（由调用方捕获处理）
     */
    public static CheckinStatus of(String status) {
        if (status == null) {
            return null;
        }
        try {
            return CheckinStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
