package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 签到核销入参 DTO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "签到核销请求")
public class CheckinVerifyDTO {

    @Schema(description = "核销码（6 位数字）", example = "483920")
    @NotBlank(message = "核销码不能为空")
    private String checkinCode;

    @Schema(description = "核销备注")
    private String remark;
}
