package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "用户信息")
public class SysUserVO {

    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "性别")
    private Integer gender;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "状态：0禁用 1启用 2待审核")
    private Integer status;

    @Schema(description = "角色ID集合")
    private List<Long> roleIds = new ArrayList<>();

    @Schema(description = "角色名称集合")
    private List<String> roleNames = new ArrayList<>();

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}