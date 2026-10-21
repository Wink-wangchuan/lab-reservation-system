package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联实体
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permissionId;
}