package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 验证码登录入参 DTO
 *
 * @author LabWise
 * @since 1.2.0
 */
@Data
@Schema(description = "验证码登录请求")
public class CodeLoginDTO {

    @Schema(description = "账号标识：用户名或手机号", example = "student")
    @NotBlank(message = "请填写用户名或手机号")
    private String account;

    @Schema(description = "短信验证码（6 位数字）", example = "483920")
    @NotBlank(message = "请填写验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;
}
