package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 设备分类实体。
 * <p>
 * 对应 SQL 真相源中的 {@code device_category} 表，支持二级分类与分类默认审批模式，
 * 后续预约创建时将依赖该默认审批模式决定初始审批链路。
 */
@TableName("device_category")
public class DeviceCategory {

    /** 分类主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 分类名称，同一父分类下必须唯一。 */
    private String name;

    /** 父分类 ID，根分类为空。 */
    @TableField("parent_id")
    private String parentId;

    /** 排序序号。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 分类描述。 */
    private String description;

    /** 分类默认审批模式。 */
    @TableField("default_approval_mode")
    private String defaultApprovalMode;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDefaultApprovalMode() { return defaultApprovalMode; }
    public void setDefaultApprovalMode(String defaultApprovalMode) { this.defaultApprovalMode = defaultApprovalMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
