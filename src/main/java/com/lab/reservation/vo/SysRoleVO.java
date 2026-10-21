package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统角色 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "角色信息")
public class SysRoleVO {

    private Long id;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "成人本角色权限ID集合")
    private List<Long> permIds = new ArrayList<>();

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}