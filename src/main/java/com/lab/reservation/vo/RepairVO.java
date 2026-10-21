package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备报修单 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "设备报修单信息")
public class RepairVO {

    private Long id;

    @Schema(description = "报修单号")
    private String repairNo;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "所属实验室ID")
    private Long labId;

    @Schema(description = "报修人用户ID")
    private Long reporterId;

    @Schema(description = "报修人姓名")
    private String reporterName;

    @Schema(description = "故障描述")
    private String faultDesc;

    @Schema(description = "状态编码 PENDING/REPAIRING/COMPLETED/CANCELLED")
    private String status;

    @Schema(description = "状态中文")
    private String statusDesc;

    @Schema(description = "处理人用户ID")
    private Long handlerId;

    @Schema(description = "处理人姓名")
    private String handlerName;

    @Schema(description = "处理意见")
    private String handleRemark;

    @Schema(description = "处理开始时间")
    private LocalDateTime startTime;

    @Schema(description = "处理完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;
}