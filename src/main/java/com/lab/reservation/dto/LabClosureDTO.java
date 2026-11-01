package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 闭馆日历入参
 *
 * @author LabWise
 * @since 3.7.0
 */
@Data
@Schema(description = "闭馆日历请求")
public class LabClosureDTO {

    private Long id;

    @Schema(description = "实验室ID；null 或 0 表示全部实验室")
    private Long labId;

    @Schema(description = "闭馆开始日期")
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @Schema(description = "闭馆结束日期（含）")
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Schema(description = "闭馆原因")
    private String reason;
}
