package com.jhun.backend.service;

import com.jhun.backend.dto.role.RoleResponse;
import com.jhun.backend.dto.role.UpdateRolePermissionsRequest;
import java.util.List;

/**
 * 角色权限服务。
 */
public interface RoleService {

    List<RoleResponse> listRoles();

    void updateRolePermissions(String roleId, UpdateRolePermissionsRequest request);
}
