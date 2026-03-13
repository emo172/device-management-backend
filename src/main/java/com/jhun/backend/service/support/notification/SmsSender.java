package com.jhun.backend.service.support.notification;

/**
 * 短信发送器占位接口。
 * <p>
 * 当前阶段仅保留 SMS 渠道扩展点，不在本期实现真实短信能力。
 */
public interface SmsSender {

    void send(String phone, String content);
}
