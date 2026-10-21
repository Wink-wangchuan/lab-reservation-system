package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 热门实验室 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "热门实验室排行项")
public class HotLabVO {

    @Schema(description = "实验室ID")
    private Long labId;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "预约次数")
    private Double bookingCount;
}