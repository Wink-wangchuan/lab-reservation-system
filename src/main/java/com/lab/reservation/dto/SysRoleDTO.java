package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 系统角色新增/编辑入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "角色请求")
public class SysRoleDTO {

    private Long id; // 编辑时传递

    @Schema(description = "角色编码")
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "分配的权限ID集合")
    private List<Long> permIds;
}