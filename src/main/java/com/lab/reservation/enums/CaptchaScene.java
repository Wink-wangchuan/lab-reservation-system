package com.lab.reservation.enums;

import lombok.Getter;

/**
 * 验证码使用场景
 *
 * <p>不同场景的验证码互不通用，通过独立的 Redis key 前缀隔离：
 * 登录用的码不能用于注册或找回密码，反之亦然。</p>
 *
 * @author LabWise
 * @since 1.2.0
 */
@Getter
public enum CaptchaScene {

    /** 验证码登录（标识为解析后的用户ID） */
    LOGIN("验证码登录", "login"),
    /** 自助注册（标识为手机号，此时用户尚不存在） */
    REGISTER("账号注册", "register"),
    /** 找回密码（标识为解析后的用户ID） */
    RESET("找回密码", "reset");

    /** 中文描述（用于提示文案） */
    private final String desc;

    /** Redis key 前缀片段 */
    private final String prefix;

    CaptchaScene(String desc, String prefix) {
        this.desc = desc;
        this.prefix = prefix;
    }

    /**
     * 字符串转枚举，非法返回 null（由调用方捕获处理）
     */
    public static CaptchaScene of(String scene) {
        if (scene == null) {
            return null;
        }
        try {
            return CaptchaScene.valueOf(scene.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
