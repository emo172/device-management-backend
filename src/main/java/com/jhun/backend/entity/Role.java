package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 角色实体。
 * <p>
 * 对应 SQL 真相源中的 {@code role} 表，当前阶段用于固定三角色模型的查询与绑定，
 * 禁止在实现中继续引入历史口径中的单一 {@code ADMIN} 角色。
 */
@TableName("role")
public class Role {

    /** 角色主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 角色编码名称，仅允许 USER、DEVICE_ADMIN、SYSTEM_ADMIN。 */
    private String name;

    /** 角色描述，供管理端与审计展示。 */
    private String description;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
