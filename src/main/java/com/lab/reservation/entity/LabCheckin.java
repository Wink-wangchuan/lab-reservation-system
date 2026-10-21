package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约签到核销单实体
 *
 * <p style="color:#c00"><b>答辩亮点：</b>预约审批通过后自动生成核销码，
 * 到场核销签到、离场签退，形成「提交-审批-签到-完成」的完整业务闭环。</p>
 *
 * <p>预约日期与时段以冗余快照落库，避免每次读取都要回查预约单，
 * 同时便于独立判定「超时未签到」与「超时未签退」。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_checkin")
public class LabCheckin extends BaseEntity {

    /** 核销码（6 位数字，全局唯一） */
    private String checkinCode;

    /** 预约单ID */
    private Long reservationId;

    /** 预约单号（冗余快照） */
    private String reservationNo;

    /** 实验室ID */
    private Long labId;

    /** 实验室名称（冗余快照） */
    private String labName;

    /** 使用人用户ID */
    private Long userId;

    /** 使用人姓名（冗余快照） */
    private String userName;

    /** 预约日期（Java 25 LocalDate） */
    private LocalDate reserveDate;

    /** 预约开始时间（Java 25 LocalTime） */
    private LocalTime startTime;

    /** 预约结束时间（Java 25 LocalTime） */
    private LocalTime endTime;

    /** 状态：SIGN_IN_PENDING/CHECKED_IN/COMPLETED/EXPIRED/CANCELLED */
    private String status;

    /** 实际签到时间 */
    private LocalDateTime checkinTime;

    /** 实际签退时间 */
    private LocalDateTime checkoutTime;

    /** 核销人（管理员/教师）用户ID */
    private Long checkinById;

    /** 核销人姓名（冗余快照） */
    private String checkinByName;

    /** 核销备注 */
    private String remark;
}
