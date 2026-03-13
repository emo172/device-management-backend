package com.jhun.backend.service;

import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;
import java.util.List;

/**
 * 通知服务。
 */
public interface NotificationService {

    List<NotificationResponse> listNotifications(String userId);

    UnreadCountResponse getUnreadCount(String userId);

    MarkReadResponse markAsRead(String userId, String notificationId);

    MarkAllReadResponse markAllAsRead(String userId);
}
