package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 预约实体。
 * <p>
 * 对应 SQL 真相源中的 {@code reservation} 表，当前阶段重点承载预约创建、一审、二审和签到前状态机字段。
 */
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("batch_id")
    private String batchId;
    @TableField("user_id")
    private String userId;
    @TableField("created_by")
    private String createdBy;
    @TableField("reservation_mode")
    private String reservationMode;
    @TableField("device_id")
    private String deviceId;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
    private String purpose;
    private String status;
    @TableField("approval_mode_snapshot")
    private String approvalModeSnapshot;
    private String remark;
    @TableField("device_approver_id")
    private String deviceApproverId;
    @TableField("device_approved_at")
    private LocalDateTime deviceApprovedAt;
    @TableField("device_approval_remark")
    private String deviceApprovalRemark;
    @TableField("system_approver_id")
    private String systemApproverId;
    @TableField("system_approved_at")
    private LocalDateTime systemApprovedAt;
    @TableField("system_approval_remark")
    private String systemApprovalRemark;
    @TableField("sign_status")
    private String signStatus;
    @TableField("checked_in_at")
    private LocalDateTime checkedInAt;
    @TableField("cancel_reason")
    private String cancelReason;
    @TableField("cancel_time")
    private LocalDateTime cancelTime;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getReservationMode() { return reservationMode; }
    public void setReservationMode(String reservationMode) { this.reservationMode = reservationMode; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalModeSnapshot() { return approvalModeSnapshot; }
    public void setApprovalModeSnapshot(String approvalModeSnapshot) { this.approvalModeSnapshot = approvalModeSnapshot; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getDeviceApproverId() { return deviceApproverId; }
    public void setDeviceApproverId(String deviceApproverId) { this.deviceApproverId = deviceApproverId; }
    public LocalDateTime getDeviceApprovedAt() { return deviceApprovedAt; }
    public void setDeviceApprovedAt(LocalDateTime deviceApprovedAt) { this.deviceApprovedAt = deviceApprovedAt; }
    public String getDeviceApprovalRemark() { return deviceApprovalRemark; }
    public void setDeviceApprovalRemark(String deviceApprovalRemark) { this.deviceApprovalRemark = deviceApprovalRemark; }
    public String getSystemApproverId() { return systemApproverId; }
    public void setSystemApproverId(String systemApproverId) { this.systemApproverId = systemApproverId; }
    public LocalDateTime getSystemApprovedAt() { return systemApprovedAt; }
    public void setSystemApprovedAt(LocalDateTime systemApprovedAt) { this.systemApprovedAt = systemApprovedAt; }
    public String getSystemApprovalRemark() { return systemApprovalRemark; }
    public void setSystemApprovalRemark(String systemApprovalRemark) { this.systemApprovalRemark = systemApprovalRemark; }
    public String getSignStatus() { return signStatus; }
    public void setSignStatus(String signStatus) { this.signStatus = signStatus; }
    public LocalDateTime getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(LocalDateTime checkedInAt) { this.checkedInAt = checkedInAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
