package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量审批入参：一次对多张待审核预约执行相同的审批动作与意见
 *
 * <p>单批最多 50 条，避免一次长事务把审批流水全部锁住；
 * 逐单独立事务执行，个别失败不影响其余单据，失败明细回传给前端汇总展示。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "批量审批请求")
public class BatchApprovalDTO {

    @Schema(description = "预约单ID列表")
    @NotEmpty(message = "请选择要审批的预约单")
    @Size(max = 50, message = "单次最多批量审批 50 条")
    private List<Long> reservationIds;

    @Schema(description = "审批动作：APPROVE通过 / REJECT驳回", example = "APPROVE")
    @NotBlank(message = "审批动作不能为空")
    private String action;

    @Schema(description = "审批意见（驳回时作为驳回原因）")
    private String comment;
}
