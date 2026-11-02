package com.lab.reservation.security;

import com.lab.reservation.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>基于 jjwt 0.12.x 的新版 API（builder / parser 链式），
 * 负责 Token 的签发、解析与会话标识（subject 存 userId）。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 由配置密钥生成 HMAC-SHA 签名密钥
        this.key = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token（subject = 用户ID）
     */
    public String generateToken(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Date issuedAt = toDate(now);
        Date expiration = toDate(now.plusNanos(jwtProperties.getExpiration() * 1_000_000));
        return Jwts.builder()
                .subject(String.valueOf(userId))     // 会话主体：用户ID
                .issuedAt(issuedAt)                  // 签发时间
                .expiration(expiration)              // 过期时间
                .signWith(key)                       // 签名算法自动从密钥长度推导
                .compact();
    }

    /**
     * 解析 Token 内用户ID，Token 非法/过期时抛异常
     */
    public Long parseUserId(String token) throws JwtException, IllegalArgumentException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 校验 Token 是否合法（捕获异常不抛出）
     */
    public boolean validate(String token) {
        try {
            parseUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取过期时间（用于刷新 Redis 缓存 TTL）
     */
    public Date getExpirationDate(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return claims.getExpiration();
    }

    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}