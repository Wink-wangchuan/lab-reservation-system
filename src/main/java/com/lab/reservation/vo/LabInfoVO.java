package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实验室信息 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "实验室信息")
public class LabInfoVO {

    private Long id;

    @Schema(description = "实验室编号")
    private String labCode;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "实验室类型")
    private String labType;

    @Schema(description = "所在位置")
    private String location;

    @Schema(description = "容纳人数")
    private Integer capacity;

    @Schema(description = "设备配置说明")
    private String equipmentDesc;

    @Schema(description = "负责的实验室管理员ID")
    private Long managerId;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "是否开放预约：0否 1是")
    private Integer openStatus;

    @Schema(description = "是否启用预约功能：0否 1是")
    private Integer enableReserve;

    @Schema(description = "时段占用模式：1可多人共享 0整间独占")
    private Integer shareMode;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "故障锁定：true=存在故障设备，预约被锁定")
    private Boolean deviceFault;

    @Schema(description = "今日预约数")
    private Long todayReservationCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}