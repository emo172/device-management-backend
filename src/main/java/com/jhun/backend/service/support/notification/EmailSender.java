package com.jhun.backend.service.support.notification;

/**
 * 邮件发送器占位接口。
 * <p>
 * 当前阶段优先交付通知查询和已读能力，邮件发送链路暂保留扩展接口，后续接入真实 SMTP 发送实现。
 */
public interface EmailSender {

    void send(String to, String subject, String content);
}
