package com.lab.reservation.service;

import com.lab.reservation.dto.CaptchaSendDTO;
import com.lab.reservation.dto.CodeLoginDTO;
import com.lab.reservation.dto.LoginDTO;
import com.lab.reservation.dto.RegisterDTO;
import com.lab.reservation.dto.ResetPasswordDTO;
import com.lab.reservation.vo.CaptchaVO;
import com.lab.reservation.vo.LoginVO;
import com.lab.reservation.vo.UserInfoVO;

/**
 * 认证服务接口
 *
 * <p style="color:#c00"><b>答辩亮点：</b>密码登录与验证码登录共用同一套令牌签发链路，
 * 自助注册进入「待审核」状态由管理员放行，构成完整的账号生命周期。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 密码登录（用户名 + 密码）
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 获取验证码；登录场景的账号解析在此完成，注册场景以手机号自身为标识
     */
    CaptchaVO sendCaptcha(CaptchaSendDTO dto);

    /**
     * 验证码登录（用户名或手机号均可作为标识）
     */
    LoginVO loginByCode(CodeLoginDTO dto);

    /**
     * 自助注册：注册成功即为「待审核」状态，并通知管理员审核
     */
    void register(RegisterDTO dto);

    /**
     * 忘记密码：手机验证码校验通过后重置新密码
     */
    void resetPassword(ResetPasswordDTO dto);

    /**
     * 退出登录（清除 Redis 中的登录用户缓存）
     */
    void logout();

    /**
     * 获取当前登录用户信息
     */
    UserInfoVO getCurrentUserInfo();
}
