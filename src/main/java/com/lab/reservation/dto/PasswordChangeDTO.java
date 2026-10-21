package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参 DTO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "修改密码请求")
public class PasswordChangeDTO {

    @Schema(description = "原密码")
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @Schema(description = "新密码（6-32 位）")
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "新密码长度需为 6-32 位")
    private String newPassword;
}
