package com.jhun.backend.dto.reservationbatch;

/**
 * 预约批次响应。
 */
public record ReservationBatchResponse(
        String id,
        String batchNo,
        String createdBy,
        Integer reservationCount,
        Integer successCount,
        Integer failedCount,
        String status) {
}
