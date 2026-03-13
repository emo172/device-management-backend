package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
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
 * 提供角色查询和角色权限更新接口，其中权限更新必须严格限制为系统管理员才能执行。
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

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Result<Void> updateRolePermissions(
            @PathVariable("id") String roleId,
            @RequestBody UpdateRolePermissionsRequest request) {
        roleService.updateRolePermissions(roleId, request);
        return Result.success(null);
    }
}
