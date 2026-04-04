package com.jhun.backend.service.support.ai;

/**
 * Qwen 结构化提取 Prompt 规格。
 * <p>
 * 该记录类型是 task 4 为后续 provider 准备的稳定真相源：它同时保留意图识别模板、信息提取模板、统一 schema 与最终上游 Prompt，
 * 并把“必须单轮输入 + 必须包含 JSON 关键词 + sessionId 不上送”这三条边界显式固化下来。
 *
 * @param intentTemplate 意图识别模板正文
 * @param infoExtractionTemplate 信息提取模板正文
 * @param schemaJson 统一结构化提取 schema
 * @param upstreamPrompt 最终发往上游模型的完整单轮 Prompt
 * @param conversationStrategy 上游对话策略说明
 * @param sessionIdPolicy sessionId 使用边界说明
 */
public record QwenStructuredExtractionPrompt(
        String intentTemplate,
        String infoExtractionTemplate,
        String schemaJson,
        String upstreamPrompt,
        String conversationStrategy,
        String sessionIdPolicy) {

    public QwenStructuredExtractionPrompt {
        intentTemplate = intentTemplate == null ? "" : intentTemplate.trim();
        infoExtractionTemplate = infoExtractionTemplate == null ? "" : infoExtractionTemplate.trim();
        schemaJson = schemaJson == null ? "" : schemaJson.trim();
        upstreamPrompt = upstreamPrompt == null ? "" : upstreamPrompt.trim();
        conversationStrategy = conversationStrategy == null ? "" : conversationStrategy.trim();
        sessionIdPolicy = sessionIdPolicy == null ? "" : sessionIdPolicy.trim();

        QwenExtractionSchema.PromptValidationResult validationResult = QwenExtractionSchema.validateJsonModePrompt(upstreamPrompt);
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.failureReason());
        }
    }

    /**
     * JSON Mode 是否为该 Prompt 的强制要求。
     * <p>
     * 结构化提取阶段必须输出统一 JSON 对象，因此后续 provider 只要消费该规格，就必须一并开启 JSON Mode。
     *
     * @return 固定为 true
     */
    public boolean requiresJsonMode() {
        return true;
    }
}
