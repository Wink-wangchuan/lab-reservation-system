package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统权限新增/编辑入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "权限请求")
public class SysPermissionDTO {

    private Long id; // 编辑时传递

    @Schema(description = "父级ID，0为顶级")
    private Long parentId;

    @Schema(description = "权限名称")
    @NotBlank(message = "权限名称不能为空")
    private String permName;

    @Schema(description = "权限标识")
    private String permCode;

    @Schema(description = "类型：1菜单 2按钮")
    private Integer permType;

    @Schema(description = "路由地址")
    private String path;

    @Schema(description = "前端组件")
    private String component;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;
}