package com.jhun.backend.service.support.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 邮件发送器日志占位实现。
 * <p>
 * 当前阶段仓库尚未接入真实 SMTP，因此先把认证域和通知域统一收敛到邮件发送抽象，
 * 这样密码重置链路至少会走正式发送通道而不是仅把验证码滞留在服务端内存中；
 * 后续替换为真实邮件实现时，只需要在该组件内对接基础设施即可。
 */
@Component
@Profile({"dev", "test"})
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String content) {
        log.info("邮件发送占位实现，to={}, subject={}, content={}", to, subject, content);
    }
}
