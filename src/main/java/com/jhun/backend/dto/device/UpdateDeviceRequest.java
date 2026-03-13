package com.jhun.backend.dto.device;

/**
 * 更新设备请求。
 */
public record UpdateDeviceRequest(
        String name,
        String categoryName,
        String status,
        String description,
        String location) {
}
