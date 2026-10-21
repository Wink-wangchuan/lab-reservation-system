package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询某用户拥有的角色编码集合（RBAC 数据源之一）
     */
    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.deleted = 0
              AND r.status = 1
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询拥有指定角色编码的启用用户ID集合（用于定向推送通知，如通知管理员审核新注册账号）
     *
     * <p>手写 SQL 不会自动附加逻辑删除条件，此处显式过滤角色与用户的 deleted，
     * 避免把通知推给已删除或已停用的账号。</p>
     */
    @Select("""
            SELECT u.id
            FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_code = #{roleCode}
              AND r.deleted = 0
              AND r.status = 1
              AND u.deleted = 0
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 查询拥有指定权限编码的启用用户ID集合（用于「按职责定向推送」，如通知所有可审批预约的人）
     *
     * <p>同样显式过滤角色/权限/用户的逻辑删除与停用状态，避免推送给无效账号。</p>
     */
    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON r.id = ur.role_id
            INNER JOIN sys_role_permission rp ON rp.role_id = r.id
            INNER JOIN sys_permission p ON p.id = rp.permission_id
            WHERE p.perm_code = #{permCode}
              AND p.deleted = 0
              AND r.deleted = 0
              AND r.status = 1
              AND u.deleted = 0
              AND u.status = 1
            """)
    List<Long> selectUserIdsByPermission(@Param("permCode") String permCode);
}