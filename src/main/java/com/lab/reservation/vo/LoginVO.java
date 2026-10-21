package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功返回 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "令牌类型（Bearer）")
    private String tokenType;

    @Schema(description = "令牌过期时间戳（毫秒）")
    private Long expiresIn;

    @Schema(description = "用户信息")
    private UserInfoVO userInfo;
}