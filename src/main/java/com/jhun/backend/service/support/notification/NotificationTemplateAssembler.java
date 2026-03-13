package com.jhun.backend.service.support.notification;

import org.springframework.stereotype.Component;

/**
 * 通知模板装配器。
 * <p>
 * 当前阶段先保留标题与内容透传能力，后续接入模板变量替换和多渠道内容装配时在此集中扩展。
 */
@Component
public class NotificationTemplateAssembler {

    public String renderTitle(String title) {
        return title;
    }

    public String renderContent(String content) {
        return content;
    }
}
