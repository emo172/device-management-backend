package com.jhun.backend.dto.auth;

/**
 * 重置密码请求。
 *
 * @param email 邮箱地址
 * @param verificationCode 验证码
 * @param newPassword 新密码
 */
public record ResetPasswordRequest(String email, String verificationCode, String newPassword) {
}
