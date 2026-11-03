package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备报修单实体（报修流程闭环）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_repair")
public class DeviceRepair extends BaseEntity {

    /** 报修单号 */
    private String repairNo;

    /** 设备ID */
    private Long deviceId;

    /** 设备名称（冗余快照） */
    private String deviceName;

    /** 所属实验室ID */
    private Long labId;

    /** 报修人用户ID */
    private Long reporterId;

    /** 报修人姓名 */
    private String reporterName;

    /** 故障描述 */
    private String faultDesc;

    /** 优先级：LOW/NORMAL/HIGH/URGENT（决定默认 SLA 时长） */
    private String priority;

    /** 状态：PENDING/REPAIRING/COMPLETED/CANCELLED */
    private String status;

    /** 处理人用户ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 指派处理人ID（管理员指定，可为空） */
    private Long assigneeId;

    /** 指派处理人姓名 */
    private String assigneeName;

    /** 预计完成时间（SLA 截止） */
    private LocalDateTime expectedFinishTime;

    /** 超时提醒是否已推送：0否 1是 */
    private Integer slaNotified;

    /** 处理意见 */
    private String handleRemark;

    /** 维修开始时间 */
    private LocalDateTime startTime;

    /** 维修完成时间 */
    private LocalDateTime finishTime;
}