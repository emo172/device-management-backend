package com.jhun.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.role.RolePermissionTreeNodeResponse;
import com.jhun.backend.dto.role.RolePermissionTreeResponse;
import com.jhun.backend.dto.role.RolePermissionTreeRow;
import com.jhun.backend.dto.role.RoleResponse;
import com.jhun.backend.dto.role.UpdateRolePermissionsRequest;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.RolePermission;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.RolePermissionMapper;
import com.jhun.backend.service.RoleService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.jhun.backend.util.UuidUtil;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色权限服务实现。
 * <p>
 * 当前阶段提供角色列表查询、权限树读取和权限重绑能力，满足后台角色管理页的查看、回显与更新闭环。
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

    /**
     * 读取指定角色的权限树。
     * <p>
     * 服务层先校验角色存在，避免前端拿到一个不存在角色的“空树”误以为是无权限角色；
     * 随后基于 SQL 返回的扁平结果按模块分组，并把每个权限节点的 selected 原样回显给前端树组件。
     *
     * @param roleId 目标角色主键 ID
     * @return 按模块聚合后的权限树
     */
    @Override
    public List<RolePermissionTreeResponse> getRolePermissionTree(String roleId) {
        mustFindRole(roleId);
        List<RolePermissionTreeRow> rows = roleMapper.findPermissionTreeRows(roleId);
        Map<String, List<RolePermissionTreeNodeResponse>> moduleMap = new LinkedHashMap<>();
        for (RolePermissionTreeRow row : rows) {
            moduleMap.computeIfAbsent(row.getModule(), ignored -> new ArrayList<>())
                    .add(new RolePermissionTreeNodeResponse(
                            row.getPermissionId(),
                            row.getCode(),
                            row.getName(),
                            row.getDescription(),
                            row.isSelected()));
        }
        return moduleMap.entrySet().stream()
                .map(entry -> new RolePermissionTreeResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public void updateRolePermissions(String roleId, UpdateRolePermissionsRequest request) {
        mustFindRole(roleId);
        rolePermissionMapper.delete(new QueryWrapper<RolePermission>().eq("role_id", roleId));
        for (String permissionId : request.permissionIds()) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setId(UuidUtil.randomUuid());
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    /**
     * 确认目标角色存在。
     * <p>
     * 无论是读取权限树还是覆盖权限绑定，都必须先确认角色 ID 有效，
     * 否则接口返回空结果会掩盖错误入参，后续还可能把无效 ID 当成真实角色继续处理。
     *
     * @param roleId 角色主键 ID
     * @return 已确认存在的角色实体
     */
    private Role mustFindRole(String roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }
}
