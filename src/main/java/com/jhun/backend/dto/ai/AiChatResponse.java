package com.jhun.backend.dto.ai;

/**
 * AI 对话响应。
 *
 * @param id 历史记录 ID
 * @param sessionId 会话 ID
 * @param intent 识别意图
 * @param executeResult 执行结果
 * @param aiResponse AI 文本回复
 */
public record AiChatResponse(
        String id,
        String sessionId,
        String intent,
        String executeResult,
        String aiResponse) {
}
