package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;

/**
 * 代预约请求。
 * <p>
 * 仅允许 SYSTEM_ADMIN 代 USER 发起预约，因此请求中必须携带目标用户 ID，
 * 同时复用单条预约所需的设备与时间窗口字段。
 */
public record ProxyReservationRequest(
        String targetUserId,
        String deviceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String remark) {
}
