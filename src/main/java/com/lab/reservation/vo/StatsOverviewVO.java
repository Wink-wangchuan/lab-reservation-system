package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统计概览卡片 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "统计概览")
public class StatsOverviewVO {

    @Schema(description = "实验室总数")
    private Long labTotal;

    @Schema(description = "设备总数")
    private Long deviceTotal;

    @Schema(description = "用户总数")
    private Long userTotal;

    @Schema(description = "预约总次数")
    private Long reservationTotal;

    @Schema(description = "今日预约数")
    private Long todayReservation;

    @Schema(description = "待审核预约数")
    private Long pendingApproval;

    @Schema(description = "待处理报修数")
    private Long pendingRepair;

    @Schema(description = "故障设备数")
    private Long faultDevice;
}