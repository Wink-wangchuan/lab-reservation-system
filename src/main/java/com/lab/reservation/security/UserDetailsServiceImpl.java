package com.lab.reservation.security;

import com.lab.reservation.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 标准 UserDetails 加载器
 *
 * <p>用户名/密码认证流程中，由 DaoAuthenticationProvider 调用，
 * 实际业务装配委托给 {@link PermissionService}。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final PermissionService permissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoginUser loginUser = permissionService.loadLoginUserByUsername(username);
        if (loginUser == null) {
            throw new UsernameNotFoundException("用户名不存在：" + username);
        }
        return loginUser;
    }
}