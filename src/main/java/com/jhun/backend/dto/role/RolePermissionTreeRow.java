package com.jhun.backend.dto.role;

/**
 * 角色权限树查询行。
 * <p>
 * 该 DTO 仅用于承接 Mapper 联表结果：每一行代表某个模块下的一条权限以及它是否已绑定到目标角色。
 * Service 会基于该扁平结果再组装为真正返回给前端的树结构，从而把 SQL 查询职责与响应整形职责分离。
 */
public class RolePermissionTreeRow {

    /** 权限所属模块代码，是树形分组的唯一依据。 */
    private String module;

    /** 权限主键 ID，供前端更新授权时回传。 */
    private String permissionId;

    /** 权限动作代码，例如 VIEW、AUTH。 */
    private String code;

    /** 权限显示名称。 */
    private String name;

    /** 权限业务说明。 */
    private String description;

    /** 目标角色当前是否已绑定该权限。 */
    private boolean selected;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
