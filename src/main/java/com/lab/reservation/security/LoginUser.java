package com.lab.reservation.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录用户主体（Spring Security 的 Principal）
 *
 * <p>实现 {@link UserDetails}，作为 JWT 认证后写入 SecurityContext 的认证对象。
 * {@link #getAuthorities()} 同时暴露角色（ROLE_ 前缀）与权限编码，
 * 供 {@code hasRole} / {@code hasAuthority} 方法级权限校验使用。</p>
 *
 * <p style="color:#c00"><b>答辩重点：对象入缓存的一致性</b></p>
 * 该对象会整体写入 Redis。因此只读 getter（{@code authorities}、各接口标志位、
 * 密码）均标注 {@code @JsonIgnore}，仅保留可往返的字段，
 * 反序列化后再由 {@code roleCodes + permissions} 就地重建授权集合，避免
 * Jackson 对 "setterless" 属性反序列化失败。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String password;
    private String realName;
    private String avatar;
    private Integer gender;
    private Integer status;

    /** 角色编码集合（如 TEACHER） */
    private Set<String> roleCodes = new HashSet<>();

    /** 权限编码集合（如 reservation:submit） */
    private Set<String> permissions = new HashSet<>();

    /**
     * 组装授权集合：角色 -> ROLE_XXX，权限 -> perm_code
     */
    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roleCodes != null) {
            roleCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        }
        if (permissions != null) {
            permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }
        return authorities;
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return this.password;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}