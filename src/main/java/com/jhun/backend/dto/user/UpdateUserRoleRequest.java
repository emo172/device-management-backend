package com.jhun.backend.dto.user;

/**
 * 更新用户角色请求。
 *
 * @param roleId 目标角色 ID
 */
public record UpdateUserRoleRequest(String roleId) {
}
