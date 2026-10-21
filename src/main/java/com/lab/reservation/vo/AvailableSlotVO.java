package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * 可预约时段 VO（学生端选择预约时段用）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "某日可预约时段")
public class AvailableSlotVO {

    @Schema(description = "时段ID")
    private Long id;

    @Schema(description = "时段名称")
    private String slotName;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "时段最大人数")
    private Integer maxPeople;

    @Schema(description = "是否已被完全占用（存在覆盖该时段的预约）")
    private Boolean occupied;

    @Schema(description = "该时段覆盖范围内的预约笔数")
    private Long reservationCount;
}