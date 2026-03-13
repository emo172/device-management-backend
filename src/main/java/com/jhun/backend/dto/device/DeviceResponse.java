package com.jhun.backend.dto.device;

/**
 * 设备响应。
 */
public record DeviceResponse(
        String id,
        String name,
        String deviceNumber,
        String categoryId,
        String categoryName,
        String status,
        String description,
        String location) {
}
