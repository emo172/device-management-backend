package com.jhun.backend.service.support.ai;

import com.jhun.backend.common.enums.PromptTemplateType;

/**
 * Prompt 模板运行时定义。
 * <p>
 * 该记录类型把“模板正文”与“运行时职责、单轮策略、sessionId 边界、JSON Mode 要求”收口在同一处，
 * 让后续 provider 可以直接复用这一真相源，而不必再在别处拼装第二套 Prompt 元数据。
 *
 * @param type 模板类型，固定来自四种正式 PromptTemplateType
 * @param templateContent 当前生效模板正文，优先数据库启用模板，否则回退默认资源模板
 * @param runtimeRole 该模板在运行时承担的职责说明
 * @param jsonModeRequired 是否要求与 JSON Mode 约束一起使用
 * @param conversationStrategy 上游对话策略说明，首发阶段固定为单轮输入
 * @param sessionIdPolicy sessionId 的正式使用边界说明
 */
public record PromptTemplateRuntimeDefinition(
        PromptTemplateType type,
        String templateContent,
        String runtimeRole,
        boolean jsonModeRequired,
        String conversationStrategy,
        String sessionIdPolicy) {

    public PromptTemplateRuntimeDefinition {
        templateContent = templateContent == null ? "" : templateContent.trim();
        runtimeRole = runtimeRole == null ? "" : runtimeRole.trim();
        conversationStrategy = conversationStrategy == null ? "" : conversationStrategy.trim();
        sessionIdPolicy = sessionIdPolicy == null ? "" : sessionIdPolicy.trim();
    }
}
