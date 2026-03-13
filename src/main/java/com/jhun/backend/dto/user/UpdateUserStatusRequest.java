package com.jhun.backend.dto.user;

/**
 * 更新用户状态请求。
 *
 * @param status 账户状态，1 正常，0 禁用
 * @param reason 状态调整原因
 */
public record UpdateUserStatusRequest(Integer status, String reason) {
}
