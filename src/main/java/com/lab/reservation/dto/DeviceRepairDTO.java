package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报修提交/处理入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "报修请求")
public class DeviceRepairDTO {

    private Long id; // 处理时需要

    @Schema(description = "设备ID")
    @NotNull(message = "设备不能为空")
    private Long deviceId;

    @Schema(description = "故障描述")
    @NotBlank(message = "故障描述不能为空")
    private String faultDesc;

    @Schema(description = "处理状态：PENDING/REPAIRING/COMPLETED/CANCELLED")
    private String status;

    @Schema(description = "处理意见")
    private String handleRemark;
}