package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证码发送入参 DTO
 *
 * <p>account 的含义随场景而变：登录场景可为用户名或手机号；
 * 注册场景为待注册的手机号；找回密码场景为已绑定手机号。</p>
 *
 * @author LabWise
 * @since 1.2.0
 */
@Data
@Schema(description = "获取验证码请求")
public class CaptchaSendDTO {

    @Schema(description = "使用场景 LOGIN/REGISTER/RESET", example = "LOGIN")
    @NotBlank(message = "验证码场景不能为空")
    private String scene;

    @Schema(description = "账号标识：用户名或手机号", example = "student")
    @NotBlank(message = "请填写账号")
    private String account;
}
