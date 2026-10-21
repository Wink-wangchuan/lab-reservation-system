package com.lab.reservation.service;

import com.lab.reservation.enums.CaptchaScene;
import com.lab.reservation.vo.CaptchaVO;

/**
 * 验证码服务接口
 *
 * <p>只负责验证码的<b>生成、存储与一次性校验</b>，不含账号解析等业务语义
 * （账号解析由 {@code AuthService} 负责，见 {@code sendCaptcha}）。</p>
 *
 * @author LabWise
 * @since 1.2.0
 */
public interface CaptchaService {

    /**
     * 生成验证码并写入缓存（同时施加发送节流）
     *
     * @param scene    使用场景
     * @param identity 标识（登录/重置为 userId，注册为手机号）
     * @return 验证码与有效期
     */
    CaptchaVO generate(CaptchaScene scene, String identity);

    /**
     * 校验并消费验证码，失败抛出业务异常（错误 / 失效 / 已被使用）
     */
    void verify(CaptchaScene scene, String identity, String code);
}
