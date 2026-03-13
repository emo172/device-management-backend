package com.jhun.backend.dto.auth;

/**
 * 更新个人资料请求。
 *
 * @param realName 真实姓名
 * @param phone 手机号
 */
public record UpdateProfileRequest(String realName, String phone) {
}
