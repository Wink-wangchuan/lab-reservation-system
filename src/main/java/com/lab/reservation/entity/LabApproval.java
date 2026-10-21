package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约审批记录实体（多级审批流水）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@TableName("lab_approval")
public class LabApproval {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预约单ID */
    private Long reservationId;

    /** 审批层级（预留多级） */
    private Integer approvalLevel;

    /** 审批人用户ID */
    private Long approverId;

    /** 审批人姓名（冗余快照） */
    private String approverName;

    /** 动作：APPROVE通过 / REJECT驳回 */
    private String action;

    /** 审批意见 */
    private String comment;

    /** 审批时间 */
    private LocalDateTime approveTime;
}