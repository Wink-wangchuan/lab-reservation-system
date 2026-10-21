package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ECharts 通用名称-数值对
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "饼图/柱状图数据点")
public class NameValueVO {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数值")
    private Long value;
}