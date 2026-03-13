package com.jhun.backend.dto.notification;

/**
 * 通知响应。
 *
 * @param id 通知 ID
 * @param notificationType 通知类型
 * @param channel 通知渠道
 * @param title 标题
 * @param content 内容
 * @param readFlag 已读标记
 */
public record NotificationResponse(
        String id,
        String notificationType,
        String channel,
        String title,
        String content,
        Integer readFlag) {
}
