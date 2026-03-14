package com.jhun.backend.dto.device;

import java.util.List;

/**
 * 设备分页响应。
 */
public record DevicePageResponse(long total, List<DeviceResponse> records) {
}
