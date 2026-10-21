package com.lab.reservation.security;

import com.lab.reservation.common.ResultCode;
import com.lab.reservation.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 *
 * <p>提供从 SecurityContext 获取当前登录用户的便捷方法，Service 层可据此
 * 拿到当前操作用户ID/角色，无需在 Controller 手动传参。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户，未登录抛出 401
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 判断当前用户是否拥有指定角色编码
     */
    public static boolean hasRole(String roleCode) {
        return getLoginUser().getRoleCodes().contains(roleCode);
    }

    /**
     * 判断当前用户是否拥有指定权限编码
     */
    public static boolean hasPermission(String permCode) {
        return getLoginUser().getPermissions().contains(permCode);
    }
}