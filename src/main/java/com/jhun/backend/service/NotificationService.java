package com.jhun.backend.service;

import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationPageResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;

/**
 * 通知服务。
 */
public interface NotificationService {

    /**
     * 分页查询通知列表。
     * <p>
     * 该接口沿用通知中心的固定分页契约，支持按通知类型做后端精确过滤，
     * 未知类型不报错而是返回空结果，便于前端在筛选项漂移时保持列表稳定。
     */
    NotificationPageResponse listNotifications(String userId, int page, int size, String notificationType);

    UnreadCountResponse getUnreadCount(String userId);

    MarkReadResponse markAsRead(String userId, String notificationId);

    MarkAllReadResponse markAllAsRead(String userId);
}
