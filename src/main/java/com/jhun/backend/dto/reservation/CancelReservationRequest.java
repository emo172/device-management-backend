package com.jhun.backend.dto.reservation;

/**
 * 取消预约请求。
 *
 * @param reason 取消原因；会写入预约取消字段，供列表页、详情页和后续审计定位取消背景
 */
public record CancelReservationRequest(String reason) {
}
