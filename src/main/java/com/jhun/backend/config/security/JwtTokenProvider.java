package com.jhun.backend.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 令牌提供器。
 * <p>
 * 当前阶段负责签发和解析访问令牌、刷新令牌，为认证接口与安全过滤链提供统一的令牌能力。
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成访问令牌。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param role 角色名称
     * @return JWT 访问令牌
     */
    public String createAccessToken(String userId, String username, String role) {
        return createToken(userId, username, role, jwtProperties.accessTokenValidity(), "access");
    }

    /**
     * 生成刷新令牌。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param role 角色名称
     * @return JWT 刷新令牌
     */
    public String createRefreshToken(String userId, String username, String role) {
        return createToken(userId, username, role, jwtProperties.refreshTokenValidity(), "refresh");
    }

    /**
     * 解析令牌载荷。
     *
     * @param token JWT 字符串
     * @return 令牌声明
     */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
    }

    private String createToken(String userId, String username, String role, long validitySeconds, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
