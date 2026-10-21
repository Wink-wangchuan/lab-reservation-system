package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.dto.CaptchaSendDTO;
import com.lab.reservation.dto.CodeLoginDTO;
import com.lab.reservation.dto.LoginDTO;
import com.lab.reservation.dto.RegisterDTO;
import com.lab.reservation.dto.ResetPasswordDTO;
import com.lab.reservation.service.AuthService;
import com.lab.reservation.vo.CaptchaVO;
import com.lab.reservation.vo.LoginVO;
import com.lab.reservation.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 *
 * <p>提供密码登录、验证码登录、自助注册与忘记密码四类匿名可访问的入口，
 * 以及需要登录态的当前用户信息与退出登录。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "认证管理", description = "登录 / 注册 / 找回密码 / 退出 / 当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录（用户名 + 密码）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success("登录成功", authService.login(loginDTO));
    }

    @Operation(summary = "获取验证码（演示环境：验证码直接随响应返回）")
    @PostMapping("/captcha")
    public Result<CaptchaVO> captcha(@Valid @RequestBody CaptchaSendDTO dto) {
        return Result.success("验证码已生成", authService.sendCaptcha(dto));
    }

    @Operation(summary = "验证码登录（用户名或手机号 + 验证码）")
    @PostMapping("/login-by-code")
    public Result<LoginVO> loginByCode(@Valid @RequestBody CodeLoginDTO dto) {
        return Result.success("登录成功", authService.loginByCode(dto));
    }

    @Operation(summary = "自助注册（注册后需管理员审核）")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success("注册成功，请等待管理员审核", null);
    }

    @Operation(summary = "忘记密码（手机验证码校验后重置）")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success("密码已重置，请使用新密码登录", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoVO> currentUserInfo() {
        return Result.success(authService.getCurrentUserInfo());
    }
}
