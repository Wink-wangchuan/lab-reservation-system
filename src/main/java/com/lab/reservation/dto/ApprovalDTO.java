package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批入参 DTO（教师审批学生预约）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "审批请求")
public class ApprovalDTO {

    @Schema(description = "预约单ID")
    @NotNull(message = "预约单不能为空")
    private Long reservationId;

    @Schema(description = "审批动作：APPROVE通过 / REJECT驳回", example = "APPROVE")
    @NotBlank(message = "审批动作不能为空")
    private String action;

    @Schema(description = "审批意见")
    private String comment;
}