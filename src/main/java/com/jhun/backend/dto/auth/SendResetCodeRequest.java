package com.jhun.backend.dto.auth;

/**
 * 发送重置验证码请求。
 *
 * @param email 邮箱地址
 */
public record SendResetCodeRequest(String email) {
}
