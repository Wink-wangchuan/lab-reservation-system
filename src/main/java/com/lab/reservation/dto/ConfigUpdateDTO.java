package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置修改入参
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "配置修改")
public class ConfigUpdateDTO {

    @Schema(description = "配置键")
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    @Schema(description = "配置值，留空表示清空")
    private String configValue;
}
