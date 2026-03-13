package com.jhun.backend.dto.auth;

/**
 * 当前用户资料响应。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param email 邮箱
 * @param realName 真实姓名
 * @param phone 手机号
 * @param role 角色名称
 */
public record CurrentUserResponse(String userId, String username, String email, String realName, String phone, String role) {
}
