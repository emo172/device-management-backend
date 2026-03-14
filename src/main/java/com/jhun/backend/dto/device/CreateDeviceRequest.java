package com.jhun.backend.dto.device;

/**
 * 创建设备请求。
 */
public record CreateDeviceRequest(
        String name,
        String deviceNumber,
        String categoryName,
        String status,
        String description,
        String location) {
}
