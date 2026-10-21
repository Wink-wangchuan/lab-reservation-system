package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.mapper.SysPermissionMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

/**
 * 权限装配服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public LoginUser loadLoginUserByUserId(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return buildLoginUser(user);
    }

    @Override
    public LoginUser loadLoginUserByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            return null;
        }
        return buildLoginUser(user);
    }

    /**
     * 组装登录主体：用户基础信息 + 角色编码 + 权限编码
     */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());
        loginUser.setRealName(user.getRealName());
        loginUser.setGender(user.getGender());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setStatus(user.getStatus());

        // RBAC 装配
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = sysPermissionMapper.selectPermCodesByUserId(user.getId());
        loginUser.setRoleCodes(roles == null ? new HashSet<>() : new HashSet<>(roles));
        loginUser.setPermissions(perms == null ? new HashSet<>() : new HashSet<>(perms));
        return loginUser;
    }
}