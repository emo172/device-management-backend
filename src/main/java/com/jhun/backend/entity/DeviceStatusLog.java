package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 设备状态变更记录实体。
 * <p>
 * 用于审计设备状态从一个业务状态流转到另一个业务状态的全过程，后续借还与维修模块会依赖该记录追溯设备历史。
 */
@TableName("device_status_log")
public class DeviceStatusLog {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("device_id")
    private String deviceId;

    @TableField("old_status")
    private String oldStatus;

    @TableField("new_status")
    private String newStatus;

    private String reason;

    @TableField("operator_id")
    private String operatorId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
