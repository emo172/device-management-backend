package com.jhun.backend.dto.notification;

import java.time.LocalDateTime;

/**
 * 通知响应。
 *
 * @param id 通知 ID
 * @param notificationType 通知类型
 * @param channel 通知渠道
 * @param title 标题
 * @param content 内容
 * @param status 通知发送状态，仅表示通知域的投递状态，不能与预约、借还状态混用
 * @param readFlag 已读标记
 * @param readAt 已读时间，仅 `IN_APP` 渠道有业务意义
 * @param templateVars 模板变量 JSON 文本，供前端在需要时做问题排查与二次渲染
 * @param retryCount 重试次数，体现通知发送链路是否经历补偿
 * @param relatedId 关联业务主键，例如预约 ID、逾期记录 ID
 * @param relatedType 关联业务类型，用于区分 `relatedId` 所属业务域
 * @param sentAt 实际发送时间，站内信可能为空但邮件/短信渠道应保留该字段
 * @param createdAt 通知创建时间，供通知页排序和回放排查使用
 */
public record NotificationResponse(
        String id,
        String notificationType,
        String channel,
        String title,
        String content,
        String status,
        Integer readFlag,
        LocalDateTime readAt,
        String templateVars,
        Integer retryCount,
        String relatedId,
        String relatedType,
        LocalDateTime sentAt,
        LocalDateTime createdAt) {
}
