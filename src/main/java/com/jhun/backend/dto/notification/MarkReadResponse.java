package com.jhun.backend.dto.notification;

import java.time.LocalDateTime;

/**
 * 标记已读响应。
 *
 * @param notificationId 通知 ID
 * @param readFlag 已读标记
 * @param readAt 已读时间，用于让通知中心和 Header 角标在单条已读后立即使用后端真实回执回写界面
 * @param unreadCount 当前用户剩余未读站内信数量，避免前端在已读动作后再自行猜测角标值
 */
public record MarkReadResponse(String notificationId, Integer readFlag, LocalDateTime readAt, long unreadCount) {
}
