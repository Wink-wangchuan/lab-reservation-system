package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心 · 基础资料 VO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "个人资料")
public class ProfileVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "性别：0男 1女 2保密")
    private Integer gender;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "角色编码集合")
    private List<String> roleCodes;

    @Schema(description = "角色名称集合")
    private List<String> roleNames;

    @Schema(description = "权限条数")
    private Integer permissionCount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
