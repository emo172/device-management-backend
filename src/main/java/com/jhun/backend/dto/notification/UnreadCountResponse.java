package com.jhun.backend.dto.notification;

/**
 * 未读数量响应。
 *
 * @param unreadCount 未读站内信数量
 */
public record UnreadCountResponse(long unreadCount) {
}
