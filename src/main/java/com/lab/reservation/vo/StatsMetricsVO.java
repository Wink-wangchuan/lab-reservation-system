package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 预约履约质量指标
 *
 * <p>三个比率都按「已产生终态的单」计算，避免把还在待审核的单计入分母造成比率虚高：</p>
 * <ul>
 *   <li>通过率 = (已通过 + 已完成) / (已通过 + 已完成 + 已驳回)</li>
 *   <li>驳回率 = 已驳回 / 同上分母</li>
 *   <li>爽约率 = 已过期签到单 / (已过期 + 已签到 + 已完成)</li>
 * </ul>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Data
@Schema(description = "预约履约质量指标")
public class StatsMetricsVO {

    @Schema(description = "已通过（含已完成）预约数")
    private Long approvedTotal;

    @Schema(description = "已驳回预约数")
    private Long rejectedTotal;

    @Schema(description = "已过期（爽约）签到单数")
    private Long noShowTotal;

    @Schema(description = "应签到的签到单总数")
    private Long checkinTotal;

    @Schema(description = "通过率（%）")
    private Double approvalRate;

    @Schema(description = "驳回率（%）")
    private Double rejectRate;

    @Schema(description = "爽约率（%）")
    private Double noShowRate;

    @Schema(description = "平均审批响应时长（分钟）")
    private Double avgApprovalMinutes;
}
