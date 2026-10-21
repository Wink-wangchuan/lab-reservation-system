package com.lab.reservation.service;

import com.lab.reservation.security.LoginUser;

/**
 * 权限装配服务接口
 *
 * <p>负责从 RBAC 数据表（sys_user / sys_user_role / sys_role /
 * sys_role_permission / sys_permission）中装载某用户的登录主体，
 * 供认证过滤器与登录流程复用。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface PermissionService {

    /**
     * 按用户ID装配登录主体（含角色与权限编码），用户不存在返回 null
     */
    LoginUser loadLoginUserByUserId(Long userId);

    /**
     * 按用户名装配登录主体
     */
    LoginUser loadLoginUserByUsername(String username);
}