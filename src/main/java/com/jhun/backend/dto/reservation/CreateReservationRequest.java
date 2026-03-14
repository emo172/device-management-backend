package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;

/**
 * 创建预约请求。
 */
public record CreateReservationRequest(
        String deviceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String remark) {
}
