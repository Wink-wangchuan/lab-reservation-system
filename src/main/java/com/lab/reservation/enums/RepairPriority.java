package com.lab.reservation.enums;

import lombok.Getter;

/**
 * 报修优先级枚举
 *
 * <p>优先级同时决定默认 SLA 时长：提交/调整优先级时若未指定预计完成时间，
 * 按「创建时间 + 对应小时数」自动推算，超时后定时任务提醒处理人。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Getter
public enum RepairPriority {

    /** 低：5 天 */
    LOW("低", 120),
    /** 普通：3 天 */
    NORMAL("普通", 72),
    /** 高：24 小时 */
    HIGH("高", 24),
    /** 紧急：4 小时 */
    URGENT("紧急", 4);

    private final String desc;

    /** 默认 SLA 小时数 */
    private final int slaHours;

    RepairPriority(String desc, int slaHours) {
        this.desc = desc;
        this.slaHours = slaHours;
    }

    public static RepairPriority of(String code) {
        if (code == null) {
            return NORMAL;
        }
        try {
            return RepairPriority.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
