package com.jhun.backend.service.support.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 基于 SMTP 的邮件发送实现。
 * <p>
 * 生产环境下密码重置验证码必须通过用户可见的真实邮件通道送达，
 * 不能退化为仅服务端日志可见的伪发送链路，因此 prod profile 下统一使用 Spring Mail 对接 SMTP。
 */
@Component
@Profile("prod")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String content) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromAddress);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(content);
        javaMailSender.send(mailMessage);
    }
}
