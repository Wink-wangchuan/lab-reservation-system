package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新入参 DTO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "个人资料更新请求")
public class ProfileUpdateDTO {

    @Schema(description = "真实姓名")
    @Size(max = 64, message = "姓名不能超过64字")
    private String realName;

    @Schema(description = "性别：0男 1女 2保密")
    private Integer gender;

    @Schema(description = "手机号")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "头像URL")
    @Size(max = 512, message = "头像地址过长")
    private String avatar;
}
