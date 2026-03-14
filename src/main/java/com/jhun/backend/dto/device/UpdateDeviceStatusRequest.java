package com.jhun.backend.dto.device;

/**
 * 更新设备状态请求。
 *
 * @param status 目标状态
 * @param reason 状态变更原因
 */
public record UpdateDeviceStatusRequest(String status, String reason) {
}
