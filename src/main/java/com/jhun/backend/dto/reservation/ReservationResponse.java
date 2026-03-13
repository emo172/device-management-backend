package com.jhun.backend.dto.reservation;

/**
 * 预约响应。
 */
public record ReservationResponse(
        String id,
        String batchId,
        String userId,
        String createdBy,
        String reservationMode,
        String deviceId,
        String status,
        String approvalModeSnapshot,
        String deviceApproverId,
        String systemApproverId) {
}
