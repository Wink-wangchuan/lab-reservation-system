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

    /** 状态：PENDING/REPAIRING/COMPLETED/CANCELLED */
    private String status;

    /** 处理人用户ID */
    private Long handlerId;

    /** 处理人姓名 */
    private String handlerName;

    /** 处理意见 */
    private String handleRemark;

    /** 维修开始时间 */
    private LocalDateTime startTime;

    /** 维修完成时间 */
    private LocalDateTime finishTime;
}