package com.jhun.backend.dto.reservation;

/**
 * 审核预约请求。
 */
public record AuditReservationRequest(Boolean approved, String remark) {
}
