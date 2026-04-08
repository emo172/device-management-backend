package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 预约-设备关联实体。
 * <p>
 * 该实体承载预约聚合与设备之间的正式真相关系：
 * 单设备预约落 1 条记录，多设备预约后续扩展为多条记录，旧 {@code reservation.device_id} 只保留兼容读取语义。
 */
@TableName("reservation_device")
public class ReservationDevice {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("reservation_id")
    private String reservationId;

    @TableField("device_id")
    private String deviceId;

    @TableField("device_order")
    private Integer deviceOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getDeviceOrder() {
        return deviceOrder;
    }

    public void setDeviceOrder(Integer deviceOrder) {
        this.deviceOrder = deviceOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
