package com.lab.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * JWT 配置属性
 *
 * <p>绑定 application.yml 中自定义的 {@code jwt.*} 配置项，
 * 注入到 Token 工具类与 Security 过滤器使用。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HS256，生产环境务必替换为强随机串） */
    private String secret;

    /** 有效期（毫秒） */
    private Long expiration;

    /** 请求头名称 */
    private String header;

    /** 令牌前缀 */
    private String prefix;
}