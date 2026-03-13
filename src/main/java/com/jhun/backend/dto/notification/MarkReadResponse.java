package com.jhun.backend.dto.notification;

/**
 * 标记已读响应。
 *
 * @param notificationId 通知 ID
 * @param readFlag 已读标记
 */
public record MarkReadResponse(String notificationId, Integer readFlag) {
}
