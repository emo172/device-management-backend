package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationPageResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知服务实现。
 * <p>
 * 当前阶段先聚焦前端通知页所需的站内信查询、未读统计与已读更新能力，邮件与短信渠道在此阶段仅保留数据结构和扩展入口。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRecordMapper notificationRecordMapper;

    public NotificationServiceImpl(NotificationRecordMapper notificationRecordMapper) {
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Override
    /**
     * 分页查询通知列表。
     * <p>
     * 这里与 borrow/overdue 分页保持同一归一化规则：page/size 小于 1 时回落到 1，
     * 并把 notificationType 精确过滤下沉到 SQL，避免先全量查出后再在内存中切片或过滤。
     */
    public NotificationPageResponse listNotifications(String userId, int page, int size, String notificationType) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = notificationRecordMapper.countByConditions(notificationType, userId);
        List<NotificationResponse> records = notificationRecordMapper
                .findPageByConditions(notificationType, userId, safeSize, offset)
                .stream()
                .map(this::toResponse)
                .toList();
        return new NotificationPageResponse(total, records);
    }

    @Override
    public UnreadCountResponse getUnreadCount(String userId) {
        return new UnreadCountResponse(notificationRecordMapper.countUnreadInAppByUserId(userId));
    }

    @Override
    @Transactional
    public MarkReadResponse markAsRead(String userId, String notificationId) {
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationRecordMapper.markAsRead(notificationId, userId, readAt);
        if (updated == 0) {
            throw new BusinessException("通知不存在或无需更新已读状态");
        }
        return new MarkReadResponse(notificationId, 1, readAt, notificationRecordMapper.countUnreadInAppByUserId(userId));
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllAsRead(String userId) {
        LocalDateTime readAt = LocalDateTime.now();
        int updatedCount = notificationRecordMapper.markAllAsRead(userId, readAt);
        return new MarkAllReadResponse(
                updatedCount,
                updatedCount > 0 ? readAt : null,
                notificationRecordMapper.countUnreadInAppByUserId(userId));
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(),
                record.getNotificationType(),
                record.getChannel(),
                record.getTitle(),
                record.getContent(),
                record.getStatus(),
                record.getReadFlag(),
                record.getReadAt(),
                record.getTemplateVars(),
                record.getRetryCount(),
                record.getRelatedId(),
                record.getRelatedType(),
                record.getSentAt(),
                record.getCreatedAt());
    }
}
