package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 预约改期入参
 *
 * <p>只允许调整日期与时段，不换实验室：换实验室会牵动设备归属、容量归属与审批职责，
 * 支持成本远高于收益，用户可「取消后重新预约」。</p>
 *
 * @author LabWise
 * @since 3.8.0
 */
@Data
@Schema(description = "预约改期请求")
public class RescheduleDTO {

    @Schema(description = "新的预约日期(yyyy-MM-dd)", example = "2026-11-05")
    @NotNull(message = "新的预约日期不能为空")
    private LocalDate reserveDate;

    @Schema(description = "新的开始时间(HH:mm)", example = "10:00")
    @NotNull(message = "新的开始时间不能为空")
    private LocalTime startTime;

    @Schema(description = "新的结束时间(HH:mm)", example = "11:40")
    @NotNull(message = "新的结束时间不能为空")
    private LocalTime endTime;
}
