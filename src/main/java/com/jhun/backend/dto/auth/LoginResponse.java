package com.jhun.backend.dto.auth;

/**
 * 登录响应。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param role 角色名称
 * @param accessToken 访问令牌
 * @param refreshToken 刷新令牌
 */
public record LoginResponse(String userId, String username, String role, String accessToken, String refreshToken) {
}
