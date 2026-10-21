package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码 / 重置密码入参 DTO
 *
 * @author LabWise
 * @since 1.2.0
 */
@Data
@Schema(description = "重置密码请求")
public class ResetPasswordDTO {

    @Schema(description = "已绑定手机号", example = "13800000005")
    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "手机验证码（6 位数字）", example = "483920")
    @NotBlank(message = "请填写验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @Schema(description = "新密码（6-32 位）")
    @NotBlank(message = "请填写新密码")
    @Size(min = 6, max = 32, message = "新密码长度需为 6-32 位")
    private String newPassword;
}
