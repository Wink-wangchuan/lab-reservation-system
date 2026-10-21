package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 系统用户新增/编辑入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "用户请求")
public class SysUserDTO {

    private Long id; // 编辑时传递

    @Schema(description = "登录账号")
    @NotBlank(message = "账号不能为空")
    private String username;

    @Schema(description = "密码（新增必填，编辑留空不改）")
    private String password;

    @Schema(description = "真实姓名")
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @Schema(description = "性别：0男 1女 2保密")
    private Integer gender;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "状态：0禁用 1启用 2待审核")
    private Integer status;

    @Schema(description = "分配的角色ID集合")
    private List<Long> roleIds;
}