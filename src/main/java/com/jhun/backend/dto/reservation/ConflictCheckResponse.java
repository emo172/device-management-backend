package com.jhun.backend.dto.reservation;

/**
 * 冲突检查响应。
 */
public record ConflictCheckResponse(boolean conflict) {
}
