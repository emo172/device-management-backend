package com.jhun.backend.service;

import com.jhun.backend.dto.role.RolePermissionTreeResponse;
import com.jhun.backend.dto.role.RoleResponse;
import com.jhun.backend.dto.role.UpdateRolePermissionsRequest;
import java.util.List;

/**
 * 角色权限服务。
 */
public interface RoleService {

    /**
     * 查询角色列表。
     * <p>
     * 该方法服务于系统管理员后台的角色列表页，只读返回固定三角色的基础信息，
     * 不承担权限展开或聚合职责，避免列表接口被过度耦合到授权树场景。
     *
     * @return 角色基础信息列表
     */
    List<RoleResponse> listRoles();

    /**
     * 读取指定角色的权限树。
     * <p>
     * 服务层需要按 `permission.module` 对全量权限进行分组，并通过 `selected` 回显目标角色的已绑定状态，
     * 以便前端直接渲染授权树而无需再次拼装模块结构。
     *
     * @param roleId 目标角色主键 ID
     * @return 模块到权限节点的树结构响应
     */
    List<RolePermissionTreeResponse> getRolePermissionTree(String roleId);

    /**
     * 更新角色权限绑定。
     * <p>
     * 当前实现采用“先清空后重绑”的方式覆盖角色权限集合，因此调用方必须提交完整权限 ID 列表，
     * 不适用于局部增删单个权限的细粒度修改场景。
     *
     * @param roleId 待更新角色主键 ID
     * @param request 权限重绑请求，包含本次最终保留的全部权限 ID
     */
    void updateRolePermissions(String roleId, UpdateRolePermissionsRequest request);
}
