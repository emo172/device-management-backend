package com.jhun.backend.dto.user;

/**
 * 用户管理响应。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param status 账户状态
 * @param freezeStatus 冻结状态
 * @param roleId 角色 ID
 */
public record UserAdminResponse(String userId, String username, Integer status, String freezeStatus, String roleId) {
}
