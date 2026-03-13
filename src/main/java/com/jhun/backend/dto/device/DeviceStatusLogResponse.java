package com.jhun.backend.dto.device;

/**
 * 设备状态日志响应。
 *
 * @param oldStatus 变更前状态
 * @param newStatus 变更后状态
 * @param reason 变更原因
 */
public record DeviceStatusLogResponse(String oldStatus, String newStatus, String reason) {
}
