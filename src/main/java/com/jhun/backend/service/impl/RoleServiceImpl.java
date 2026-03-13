package com.jhun.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jhun.backend.dto.role.RoleResponse;
import com.jhun.backend.dto.role.UpdateRolePermissionsRequest;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.RolePermission;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.RolePermissionMapper;
import com.jhun.backend.service.RoleService;
import com.jhun.backend.util.UuidUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色权限服务实现。
 * <p>
 * 当前阶段先提供角色列表查询和权限重绑能力，满足后台角色管理页最基础的查询与更新需求。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public RoleServiceImpl(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public List<RoleResponse> listRoles() {
        return roleMapper.selectList(null).stream()
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.getDescription()))
                .toList();
    }

    @Override
    @Transactional
    public void updateRolePermissions(String roleId, UpdateRolePermissionsRequest request) {
        rolePermissionMapper.delete(new QueryWrapper<RolePermission>().eq("role_id", roleId));
        for (String permissionId : request.permissionIds()) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setId(UuidUtil.randomUuid());
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }
}
