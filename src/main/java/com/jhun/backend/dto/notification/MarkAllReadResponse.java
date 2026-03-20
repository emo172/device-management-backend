package com.jhun.backend.dto.notification;

import java.time.LocalDateTime;

/**
 * 全部已读响应。
 *
 * @param updatedCount 更新数量
 * @param readAt 本次批量已读统一写入的已读时间；当没有任何记录被更新时允许为空
 * @param unreadCount 批量已读后的未读站内信数量，供前端直接同步 Header 角标
 */
public record MarkAllReadResponse(int updatedCount, LocalDateTime readAt, long unreadCount) {
}
