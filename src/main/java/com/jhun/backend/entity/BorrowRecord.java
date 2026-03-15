package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 借还记录实体。
 * <p>
 * 对应 SQL 真相源中的 {@code borrow_record} 表，用于把“预约已签到”后的实际借出、实际归还与管理员操作责任固化为可审计记录，
 * 同时作为设备状态从 {@code AVAILABLE -> BORROWED -> AVAILABLE} 正式流转的唯一业务凭据。
 */
@TableName("borrow_record")
public class BorrowRecord {

    /** 借还记录主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 关联预约 ID，同一预约只能产生一条借还记录。 */
    @TableField("reservation_id")
    private String reservationId;

    /** 关联设备 ID，用于和设备状态、设备日志联动。 */
    @TableField("device_id")
    private String deviceId;

    /** 实际借用用户 ID。 */
    @TableField("user_id")
    private String userId;

    /** 实际借出时间，允许管理员补录，但必须不晚于预计归还时间。 */
    @TableField("borrow_time")
    private LocalDateTime borrowTime;

    /** 实际归还时间，只有完成归还确认后才允许填写。 */
    @TableField("return_time")
    private LocalDateTime returnTime;

    /** 预计归还时间，直接快照预约结束时间，避免后续预约改动影响逾期基准。 */
    @TableField("expected_return_time")
    private LocalDateTime expectedReturnTime;

    /** 借还状态：BORROWED、RETURNED、OVERDUE；其中 OVERDUE 由逾期治理任务基于 expected_return_time 自动识别。 */
    private String status;

    /** 借出前检查记录，用于保留设备交接时的状态说明。 */
    @TableField("borrow_check_status")
    private String borrowCheckStatus;

    /** 归还时检查记录，用于保留设备回收时的状态说明。 */
    @TableField("return_check_status")
    private String returnCheckStatus;

    /** 备注说明，承载管理员交接补充信息。 */
    private String remark;

    /** 借出确认操作人 ID，业务上必须是 DEVICE_ADMIN。 */
    @TableField("operator_id")
    private String operatorId;

    /** 归还确认操作人 ID，业务上必须是 DEVICE_ADMIN。 */
    @TableField("return_operator_id")
    private String returnOperatorId;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDateTime getBorrowTime() { return borrowTime; }
    public void setBorrowTime(LocalDateTime borrowTime) { this.borrowTime = borrowTime; }
    public LocalDateTime getReturnTime() { return returnTime; }
    public void setReturnTime(LocalDateTime returnTime) { this.returnTime = returnTime; }
    public LocalDateTime getExpectedReturnTime() { return expectedReturnTime; }
    public void setExpectedReturnTime(LocalDateTime expectedReturnTime) { this.expectedReturnTime = expectedReturnTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBorrowCheckStatus() { return borrowCheckStatus; }
    public void setBorrowCheckStatus(String borrowCheckStatus) { this.borrowCheckStatus = borrowCheckStatus; }
    public String getReturnCheckStatus() { return returnCheckStatus; }
    public void setReturnCheckStatus(String returnCheckStatus) { this.returnCheckStatus = returnCheckStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReturnOperatorId() { return returnOperatorId; }
    public void setReturnOperatorId(String returnOperatorId) { this.returnOperatorId = returnOperatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
