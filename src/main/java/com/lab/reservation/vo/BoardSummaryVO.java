package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 实验室实时占用看板 · 汇总指标
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "实验室占用汇总")
public class BoardSummaryVO {

    @Schema(description = "实验室总数")
    private Long labTotal;

    @Schema(description = "使用中数量")
    private Long inUse;

    @Schema(description = "已预约（未开始）数量")
    private Long reserved;

    @Schema(description = "空闲数量")
    private Long idle;

    @Schema(description = "故障锁定数量")
    private Long locked;

    @Schema(description = "未开放数量")
    private Long closed;

    @Schema(description = "实时使用率（0-100，保留一位小数）")
    private Double utilization;
}
