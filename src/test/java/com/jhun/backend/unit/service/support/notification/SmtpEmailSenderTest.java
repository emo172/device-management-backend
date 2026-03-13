package com.jhun.backend.unit.service.support.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.jhun.backend.service.support.notification.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 邮件发送器测试。
 * <p>
 * 该测试锁定密码重置邮件必须经过 Spring Mail 真正下发到邮件基础设施，
 * 避免再次退化为仅服务端日志可见、用户不可见的伪发送链路。
 */
class SmtpEmailSenderTest {

    /**
     * 验证 SMTP 发送器会把收件人、主题与正文写入邮件消息并交给 JavaMailSender 发送。
     */
    @Test
    void shouldDelegateMailDeliveryToJavaMailSender() {
        JavaMailSender javaMailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        SmtpEmailSender smtpEmailSender = new SmtpEmailSender(javaMailSender, "noreply@example.com");

        smtpEmailSender.send("user@example.com", "验证码", "您的验证码为：123456");

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mailCaptor.capture());
        SimpleMailMessage mailMessage = mailCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("noreply@example.com", mailMessage.getFrom());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"user@example.com"}, mailMessage.getTo());
        org.junit.jupiter.api.Assertions.assertEquals("验证码", mailMessage.getSubject());
        org.junit.jupiter.api.Assertions.assertEquals("您的验证码为：123456", mailMessage.getText());
    }
}
