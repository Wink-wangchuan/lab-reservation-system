package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 预约提交入参 DTO
 *
 * <p>时间字段使用 Java 25 新时间 API：{@link LocalDate} 日期、{@link LocalTime} 时间。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "提交预约请求")
public class ReservationSubmitDTO {

    @Schema(description = "实验室ID", example = "101")
    @NotNull(message = "实验室不能为空")
    private Long labId;

    @Schema(description = "预约日期(yyyy-MM-dd)", example = "2026-09-15")
    @NotNull(message = "预约日期不能为空")
    private LocalDate reserveDate;

    @Schema(description = "开始时间(HH:mm)", example = "08:00")
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @Schema(description = "结束时间(HH:mm)", example = "09:40")
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @Schema(description = "用途说明")
    @NotBlank(message = "用途说明不能为空")
    @Size(max = 512, message = "用途说明不能超过512字")
    private String purpose;

    @Schema(description = "使用人数", example = "30")
    @NotNull(message = "使用人数不能为空")
    @Min(value = 1, message = "使用人数至少为1")
    private Integer peopleCount;

    @Schema(description = "使用的设备ID集合")
    private List<Long> useDeviceIds;
}