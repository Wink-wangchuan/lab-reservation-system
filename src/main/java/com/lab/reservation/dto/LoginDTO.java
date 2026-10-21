package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO {

    @Schema(description = "登录账号", example = "student")
    @NotBlank(message = "账号不能为空")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    private String password;
}