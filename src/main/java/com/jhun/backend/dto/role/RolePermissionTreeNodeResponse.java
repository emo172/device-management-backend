package com.jhun.backend.dto.role;

/**
 * 角色权限树中的权限节点响应。
 * <p>
 * 该节点直接服务于前端树组件回显：
 * `permissionId` 用于提交更新时回传主键，`code/name/description` 用于展示权限语义，`selected` 用于标记当前角色是否已拥有该权限。
 * 这里显式保留模块内完整权限节点，而不是只返回已选项，避免前端自行补齐缺失权限导致口径漂移。
 *
 * @param permissionId 权限主键 ID，提交权限重绑时必须回传该值
 * @param code 权限动作代码，例如 VIEW、CREATE、AUTH
 * @param name 权限展示名称，供后台授权树直接显示
 * @param description 权限业务说明，帮助管理员理解授权边界
 * @param selected 当前角色是否已绑定该权限，供前端直接回显勾选态
 */
public record RolePermissionTreeNodeResponse(
        String permissionId,
        String code,
        String name,
        String description,
        boolean selected) {
}
