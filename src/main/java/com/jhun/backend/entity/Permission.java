package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 权限实体。
 * <p>
 * 当前阶段主要用于后续角色权限模块扩展，先按 SQL 真相源完整映射，避免后续再修改字段口径。
 */
@TableName("permission")
public class Permission {

    /** 权限主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 权限动作代码，例如 VIEW、CREATE、AUTH。 */
    private String code;

    /** 权限名称。 */
    private String name;

    /** 权限所属模块。 */
    private String module;

    /** 权限说明。 */
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

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
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
