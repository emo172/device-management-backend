package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.service.NotificationService;
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
    public List<NotificationResponse> listNotifications(String userId) {
        return notificationRecordMapper.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public UnreadCountResponse getUnreadCount(String userId) {
        return new UnreadCountResponse(notificationRecordMapper.countUnreadInAppByUserId(userId));
    }

    @Override
    @Transactional
    public MarkReadResponse markAsRead(String userId, String notificationId) {
        int updated = notificationRecordMapper.markAsRead(notificationId, userId);
        if (updated == 0) {
            throw new BusinessException("通知不存在或无需更新已读状态");
        }
        return new MarkReadResponse(notificationId, 1);
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllAsRead(String userId) {
        return new MarkAllReadResponse(notificationRecordMapper.markAllAsRead(userId));
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
