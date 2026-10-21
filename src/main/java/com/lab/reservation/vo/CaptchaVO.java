package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 验证码发送结果 VO
 *
 * <p style="color:#c00"><b>注意：本项目的验证码由服务端直接回传给前端展示。</b></p>
 * 这是<b>演示环境</b>的行为——系统未接入短信通道，为便于演示，
 * 后端生成后把验证码一并返回，前端以通知形式提示用户。
 * <b>生产环境必须改为经短信通道下发，绝不返回验证码本身</b>，
 * 否则任何人输入他人手机号即可取得验证码并登录。
 *
 * @author LabWise
 * @since 1.2.0
 */
@Data
@Schema(description = "验证码发送结果")
public class CaptchaVO {

    @Schema(description = "验证码（演示环境回传，生产环境不应返回）")
    private String code;

    @Schema(description = "有效期（秒）")
    private Integer expireSeconds;
}
