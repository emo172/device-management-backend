package com.jhun.backend.service;

import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationPageResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;
import java.util.List;

/**
 * 通知服务。
 */
public interface NotificationService {

    /**
     * 查询通知列表。
     * <p>
     * 该接口保持当前前端主线依赖的数组契约，避免通知中心在后端独立发版时因为响应结构切换而直接失配。
     */
    List<NotificationResponse> listNotifications(String userId);

    /**
     * 分页查询通知列表。
     * <p>
     * 分页能力作为独立接口承载，既保留通知中心后续服务端筛选与翻页扩展空间，
     * 也避免把当前数组接口一次性升级成分页结构后打断已上线前端契约。
     */
    NotificationPageResponse listNotificationPage(String userId, int page, int size, String notificationType);

    UnreadCountResponse getUnreadCount(String userId);

    MarkReadResponse markAsRead(String userId, String notificationId);

    MarkAllReadResponse markAllAsRead(String userId);
}
