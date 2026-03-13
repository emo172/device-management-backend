package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备实体。
 * <p>
 * 对应 SQL 真相源中的 {@code device} 表，当前阶段实现设备主数据 CRUD、分页查询与软删除，
 * 状态字段必须保持与借还、预约链路约定一致。
 */
@TableName("device")
public class Device {

    /** 设备主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 设备名称。 */
    private String name;
    /** 全局唯一设备编号。 */
    @TableField("device_number")
    private String deviceNumber;
    /** 分类 ID。 */
    @TableField("category_id")
    private String categoryId;
    /** 设备状态。 */
    private String status;
    /** 审批模式覆盖。 */
    @TableField("approval_mode_override")
    private String approvalModeOverride;
    /** 图片地址。 */
    @TableField("image_url")
    private String imageUrl;
    /** 设备说明。 */
    private String description;
    /** 购入日期。 */
    @TableField("purchase_date")
    private LocalDate purchaseDate;
    /** 存放位置。 */
    private String location;
    /** 状态变更原因。 */
    @TableField("status_change_reason")
    private String statusChangeReason;
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
    public String getDeviceNumber() { return deviceNumber; }
    public void setDeviceNumber(String deviceNumber) { this.deviceNumber = deviceNumber; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalModeOverride() { return approvalModeOverride; }
    public void setApprovalModeOverride(String approvalModeOverride) { this.approvalModeOverride = approvalModeOverride; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getStatusChangeReason() { return statusChangeReason; }
    public void setStatusChangeReason(String statusChangeReason) { this.statusChangeReason = statusChangeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
