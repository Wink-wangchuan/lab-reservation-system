package com.lab.reservation.enums;

import lombok.Getter;

/**
 * 站内消息类型
 *
 * <p>消息按业务来源分类，前端据此渲染不同色调的标签与图标。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Getter
public enum NoticeType {

    /** 系统通知（公告/广播等） */
    SYSTEM("系统通知"),
    /** 预约通知（提交/审批/签到提醒） */
    RESERVATION("预约通知"),
    /** 报修通知（报修受理/完成） */
    REPAIR("报修通知");

    private final String desc;

    NoticeType(String desc) {
        this.desc = desc;
    }

    /**
     * 字符串转枚举，非法时回退为 SYSTEM
     */
    public static NoticeType of(String type) {
        if (type == null) {
            return SYSTEM;
        }
        try {
            return NoticeType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SYSTEM;
        }
    }
}
