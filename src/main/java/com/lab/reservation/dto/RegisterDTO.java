package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助注册入参 DTO
 *
 * <p>注册成功后账号进入「待审核」状态，需管理员审核通过方可登录。</p>
 *
 * @author LabWise
 * @since 1.2.0
 */
@Data
@Schema(description = "注册请求")
public class RegisterDTO {

    @Schema(description = "登录账号（4-20 位字母/数字/下划线）", example = "zhangsan")
    @NotBlank(message = "请填写登录账号")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "账号需为 4-20 位字母、数字或下划线")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    @NotBlank(message = "请填写真实姓名")
    @Size(max = 64, message = "姓名不能超过64字")
    private String realName;

    @Schema(description = "手机号（同时作为验证码接收与登录标识）", example = "13800000007")
    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "登录密码（6-32 位）")
    @NotBlank(message = "请填写密码")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @Schema(description = "手机验证码（6 位数字）", example = "483920")
    @NotBlank(message = "请填写验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;
}
