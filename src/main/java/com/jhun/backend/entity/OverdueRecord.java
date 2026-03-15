package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 逾期记录实体。
 * <p>
 * 对应 SQL 真相源中的 {@code overdue_record} 表，用于把 borrow_record 的逾期事实、管理员处理动作、通知补发状态固化下来；
 * 该表既是逾期列表接口的数据来源，也是 C-06 逾期提醒任务与统计聚合读取的事实表，因此字段口径必须与正式 SQL 保持一致。
 */
@TableName("overdue_record")
public class OverdueRecord {

    /** 逾期记录主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 关联借还记录 ID，同一 borrow_record 只允许生成一条正式逾期记录。 */
    @TableField("borrow_record_id")
    private String borrowRecordId;

    /** 逾期用户 ID，用于列表权限、冻结策略与通知发送。 */
    @TableField("user_id")
    private String userId;

    /** 关联设备 ID，用于管理端定位逾期设备去向。 */
    @TableField("device_id")
    private String deviceId;

    /** 当前累计逾期小时数，按检测时间与 expected_return_time 的差值向下取整。 */
    @TableField("overdue_hours")
    private Integer overdueHours;

    /** 当前累计逾期天数，作为展示和统计辅助字段，避免前端重复计算。 */
    @TableField("overdue_days")
    private Integer overdueDays;

    /** 处理状态：PENDING / PROCESSED。 */
    @TableField("processing_status")
    private String processingStatus;

    /** 处理方式：WARNING / COMPENSATION / CONTINUE。 */
    @TableField("processing_method")
    private String processingMethod;

    /** 处理备注，记录管理员为什么这样处理。 */
    @TableField("processing_remark")
    private String processingRemark;

    /** 实际处理人 ID，业务上仅允许 DEVICE_ADMIN 写入。 */
    @TableField("processor_id")
    private String processorId;

    /** 处理时间。 */
    @TableField("processing_time")
    private LocalDateTime processingTime;

    /** 赔偿金额，未赔偿时保持 0。 */
    @TableField("compensation_amount")
    private BigDecimal compensationAmount;

    /** 是否已发送逾期提醒通知：0 未发送，1 已发送。 */
    @TableField("notification_sent")
    private Integer notificationSent;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBorrowRecordId() {
        return borrowRecordId;
    }

    public void setBorrowRecordId(String borrowRecordId) {
        this.borrowRecordId = borrowRecordId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getOverdueHours() {
        return overdueHours;
    }

    public void setOverdueHours(Integer overdueHours) {
        this.overdueHours = overdueHours;
    }

    public Integer getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(Integer overdueDays) {
        this.overdueDays = overdueDays;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingMethod() {
        return processingMethod;
    }

    public void setProcessingMethod(String processingMethod) {
        this.processingMethod = processingMethod;
    }

    public String getProcessingRemark() {
        return processingRemark;
    }

    public void setProcessingRemark(String processingRemark) {
        this.processingRemark = processingRemark;
    }

    public String getProcessorId() {
        return processorId;
    }

    public void setProcessorId(String processorId) {
        this.processorId = processorId;
    }

    public LocalDateTime getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(LocalDateTime processingTime) {
        this.processingTime = processingTime;
    }

    public BigDecimal getCompensationAmount() {
        return compensationAmount;
    }

    public void setCompensationAmount(BigDecimal compensationAmount) {
        this.compensationAmount = compensationAmount;
    }

    public Integer getNotificationSent() {
        return notificationSent;
    }

    public void setNotificationSent(Integer notificationSent) {
        this.notificationSent = notificationSent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
