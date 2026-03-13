package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 角色权限关联实体。
 * <p>
 * 当前阶段先建立基础映射，供后续角色权限管理直接复用。
 */
@TableName("role_permission")
public class RolePermission {

    /** 关联主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 角色 ID。 */
    @TableField("role_id")
    private String roleId;

    /** 权限 ID。 */
    @TableField("permission_id")
    private String permissionId;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
