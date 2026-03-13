package com.jhun.backend.dto.auth;

/**
 * 注册请求。
 *
 * @param username 用户名
 * @param password 明文密码
 * @param email 邮箱
 * @param realName 真实姓名
 * @param phone 手机号
 */
public record RegisterRequest(String username, String password, String email, String realName, String phone) {
}
