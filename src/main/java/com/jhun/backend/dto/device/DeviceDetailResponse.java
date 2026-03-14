package com.jhun.backend.dto.device;

import java.util.List;

/**
 * 设备详情响应。
 */
public record DeviceDetailResponse(
        String id,
        String name,
        String deviceNumber,
        String categoryId,
        String categoryName,
        String status,
        String description,
        String location,
        String imageUrl,
        List<DeviceStatusLogResponse> statusLogs) {
}
