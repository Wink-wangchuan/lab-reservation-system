package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "权限树节点")
public class PermissionNodeVO {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "父级ID")
    private Long parentId;

    @Schema(description = "权限名称")
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

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "子节点")
    private List<PermissionNodeVO> children = new ArrayList<>();
}