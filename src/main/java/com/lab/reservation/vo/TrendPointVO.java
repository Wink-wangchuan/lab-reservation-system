package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ECharts 折线图趋势点
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "趋势折线图点")
public class TrendPointVO {

    @Schema(description = "日期(yyyy-MM-dd)")
    private String date;

    @Schema(description = "当日预约数")
    private Long count;
}