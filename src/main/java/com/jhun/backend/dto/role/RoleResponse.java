package com.jhun.backend.dto.role;

/**
 * 角色列表响应。
 *
 * @param id 角色 ID
 * @param name 角色名称
 * @param description 角色说明
 */
public record RoleResponse(String id, String name, String description) {
}
