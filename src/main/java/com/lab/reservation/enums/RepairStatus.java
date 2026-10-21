package com.lab.reservation.enums;

import lombok.Getter;

import java.util.Set;

/**
 * 设备报修状态枚举（报修流程闭环）
 *
 * <pre>
 * PENDING待处理 -> REPAIRING维修中 -> COMPLETED已完成
 *                   |-> CANCELLED已取消
 * </pre>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Getter
public enum RepairStatus {

    /** 待处理 */
    PENDING("待处理"),
    /** 维修中 */
    REPAIRING("维修中"),
    /** 已完成 */
    COMPLETED("已完成"),
    /** 已取消 */
    CANCELLED("已取消");

    private final String desc;

    RepairStatus(String desc) {
        this.desc = desc;
    }

    /**
     * 从当前状态是否可流转到目标状态
     */
    public static boolean canTransition(RepairStatus from, RepairStatus to) {
        if (from == null) {
            return false;
        }
        return switch (from) {
            case PENDING -> Set.of(REPAIRING, CANCELLED).contains(to);
            case REPAIRING -> Set.of(COMPLETED, CANCELLED).contains(to);
            default -> false;
        };
    }

    public static RepairStatus of(String status) {
        if (status == null) {
            return null;
        }
        try {
            return RepairStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}