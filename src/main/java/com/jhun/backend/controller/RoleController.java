package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.role.RolePermissionTreeResponse;
import com.jhun.backend.dto.role.RoleResponse;
import com.jhun.backend.dto.role.UpdateRolePermissionsRequest;
import com.jhun.backend.service.RoleService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色权限控制器。
 * <p>
 * 提供角色查询、角色权限树读取和角色权限更新接口，全部能力都必须严格限制为系统管理员才能执行。
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Result<List<RoleResponse>> listRoles() {
        return Result.success(roleService.listRoles());
    }

    /**
     * 读取指定角色的权限树。
     * <p>
     * 该接口仅对系统管理员开放，返回“模块 -> 权限节点”的完整树结构，并用 selected 回显目标角色当前已绑定的权限；
     * 前端拿到结果后可直接渲染授权树，无需再次按模块重组或自行推断勾选状态。
     *
     * @param roleId 目标角色主键 ID
     * @return 统一响应包装后的权限树结构
     */
    @GetMapping("/{id}/permissions/tree")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Result<List<RolePermissionTreeResponse>> getRolePermissionTree(@PathVariable("id") String roleId) {
        return Result.success(roleService.getRolePermissionTree(roleId));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Result<Void> updateRolePermissions(
            @PathVariable("id") String roleId,
            @RequestBody UpdateRolePermissionsRequest request) {
        roleService.updateRolePermissions(roleId, request);
        return Result.success(null);
    }
}
