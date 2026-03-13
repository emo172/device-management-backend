package com.jhun.backend.dto.role;

import java.util.List;

/**
 * 更新角色权限请求。
 *
 * @param permissionIds 权限 ID 列表
 */
public record UpdateRolePermissionsRequest(List<String> permissionIds) {
}
