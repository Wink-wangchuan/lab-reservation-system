package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

/**
 * 当前用户信息 VO（不含敏感字段）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "性别：0男 1女 2保密")
    private Integer gender;

    @Schema(description = "角色编码集合")
    private Set<String> roles;

    @Schema(description = "权限编码集合")
    private Set<String> permissions;
}