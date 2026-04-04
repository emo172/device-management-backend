package com.jhun.backend.dto.notification;

import java.util.List;

/**
 * 通知分页响应。
 *
 * @param total 满足筛选条件的通知总数
 * @param records 当前页通知记录
 */
public record NotificationPageResponse(long total, List<NotificationResponse> records) {
}
