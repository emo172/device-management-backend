package com.jhun.backend.dto.auth;

/**
 * 登录请求。
 *
 * @param account 用户名或邮箱
 * @param password 明文密码
 */
public record LoginRequest(String account, String password) {
}
