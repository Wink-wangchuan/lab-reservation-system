package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约单实体（含状态机）
 *
 * <p style="color:#c00"><b>答辩亮点：</b>本实体承载核心业务——</p>
 * <ul>
 *   <li>状态机字段 status 流转由 {@code ReservationStatus} 枚举约束</li>
 *   <li>时间冲突判定依赖 reserveDate + startTime + endTime 三元组</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_reservation")
public class LabReservation extends BaseEntity {

    /** 预约单号 */
    private String reservationNo;

    /** 实验室ID */
    private Long labId;

    /** 实验室名称（冗余快照） */
    private String labName;

    /** 发起学生用户ID */
    private Long studentId;

    /** 学生姓名（冗余快照） */
    private String studentName;

    /** 审核教师用户ID */
    private Long teacherId;

    /** 教师姓名（冗余快照） */
    private String teacherName;

    /** 预约日期（Java 25 LocalDate） */
    private LocalDate reserveDate;

    /** 开始时间（Java 25 LocalTime） */
    private LocalTime startTime;

    /** 结束时间（Java 25 LocalTime） */
    private LocalTime endTime;

    /** 用途说明 */
    private String purpose;

    /** 使用人数 */
    private Integer peopleCount;

    /** 使用的设备ID集合（逗号分隔） */
    private String useDeviceIds;

    /** 状态：DRAFT/PENDING/APPROVED/REJECTED/COMPLETED/CANCELLED */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 取消原因 */
    private String cancelReason;

    /** 完成时间 */
    private LocalDateTime completeTime;
}