package com.jhun.backend.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 * <p>
 * 用于集中读取访问令牌与刷新令牌的签发参数，避免认证链路在多个类中散落读取配置。
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String issuer, long accessTokenValidity, long refreshTokenValidity, String secret) {
}
