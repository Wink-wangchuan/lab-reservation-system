package com.lab.reservation.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 预约状态机
 *
 * <p style="color:#c00"><b>答辩亮点：</b>状态机合法流转由本枚举集中约束，
 * 非法跳转（如 已驳回 -> 通过、已完成 -> 取消）在 Service 层直接拦截。</p>
 *
 * <pre>
 * 合法流转：
 *   DRAFT  草稿   -> PENDING(提交) | CANCELLED(删除)
 *   PENDING待审核 -> APPROVED(教师通过) | REJECTED(教师驳回) | CANCELLED(学生撤销)
 *   APPROVED已通过 -> COMPLETED(到时自动完成) | CANCELLED(取消)
 *   REJECTED/COMPLETED/CANCELLED 为终态
 * </pre>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Getter
public enum ReservationStatus {

    /** 草稿 */
    DRAFT("草稿"),
    /** 待审核 */
    PENDING("待审核"),
    /** 已通过 */
    APPROVED("已通过"),
    /** 已驳回 */
    REJECTED("已驳回"),
    /** 已完成 */
    COMPLETED("已完成"),
    /** 已取消 */
    CANCELLED("已取消");

    /** 中文描述（用于展示） */
    private final String desc;

    ReservationStatus(String desc) {
        this.desc = desc;
    }

    /**
     * 判定状态机是否允许 from -> to 合法流转
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true 允许流转
     */
    public static boolean canTransition(ReservationStatus from, ReservationStatus to) {
        if (from == null) {
            return false;
        }
        return switch (from) {
            case DRAFT -> Set.of(PENDING, CANCELLED).contains(to);
            case PENDING -> Set.of(APPROVED, REJECTED, CANCELLED).contains(to);
            case APPROVED -> Set.of(COMPLETED, CANCELLED).contains(to);
            default -> false; // REJECTED / COMPLETED / CANCELLED 为终态
        };
    }

    /**
     * 转为常量的可进入状态集合（供校验用）
     */
    public static Set<ReservationStatus> inTransitionsFrom(ReservationStatus from, ReservationStatus to) {
        return (from != null && to != null && canTransition(from, to))
                ? EnumSet.of(to)
                : EnumSet.noneOf(ReservationStatus.class);
    }

    /**
     * 字符串转枚举，非法返回 null（由调用方捕获处理）
     */
    public static ReservationStatus of(String status) {
        if (status == null) {
            return null;
        }
        try {
            return ReservationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}