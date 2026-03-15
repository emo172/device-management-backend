package com.jhun.backend.dto.role;

import java.util.List;

/**
 * 角色权限树模块响应。
 * <p>
 * 后台角色授权页按 `permission.module` 分组渲染树结构，因此服务端需要直接返回“模块 -> 权限节点”层次，
 * 避免前端重复实现分组规则并出现与 SQL 真相源不一致的模块口径。
 *
 * @param module 权限所属模块代码，例如 USER_AUTH、RESERVATION、PROMPT_TEMPLATE
 * @param permissions 当前模块下的全部权限节点，既包含已选也包含未选项
 */
public record RolePermissionTreeResponse(String module, List<RolePermissionTreeNodeResponse> permissions) {
}
