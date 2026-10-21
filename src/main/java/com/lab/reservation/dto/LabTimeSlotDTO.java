package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * 实验室开放时段入参 DTO（周维度配置）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "实验室开放时段请求")
public class LabTimeSlotDTO {

    @Schema(description = "时段ID（编辑时传递，新增可为空）")
    private Long id;

    @Schema(description = "星期：1-7 对应周一至周日")
    @NotNull(message = "星期不能为空")
    private Integer weekDay;

    @Schema(description = "时段名称")
    @NotBlank(message = "时段名称不能为空")
    private String slotName;

    @Schema(description = "开始时间")
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @Schema(description = "时段最大人数")
    private Integer maxPeople;

    @Schema(description = "启用状态：0停用 1启用")
    private Integer status;
}