package com.lab.reservation.enums;

import lombok.Getter;

/**
 * 候补队列状态
 *
 * @author LabWise
 * @since 3.7.0
 */
@Getter
public enum WaitlistStatus {

    /** 排队等待中 */
    WAITING("排队中"),
    /** 已自动递补为预约单 */
    PROMOTED("已递补"),
    /** 用户主动取消排队 */
    CANCELLED("已取消"),
    /** 时段已过或实验室不可用而失效 */
    EXPIRED("已失效");

    private final String desc;

    WaitlistStatus(String desc) {
        this.desc = desc;
    }

    /** 字符串转枚举，非法时回退为 WAITING */
    public static WaitlistStatus of(String status) {
        if (status == null) {
            return WAITING;
        }
        try {
            return WaitlistStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WAITING;
        }
    }
}
