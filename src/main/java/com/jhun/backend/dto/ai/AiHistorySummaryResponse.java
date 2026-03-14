package com.jhun.backend.dto.ai;

import java.time.LocalDateTime;

/**
 * AI 历史列表响应。
 *
 * @param id 历史记录 ID
 * @param sessionId 会话 ID
 * @param userInput 用户输入
 * @param intent 识别意图
 * @param executeResult 执行结果
 * @param createdAt 创建时间
 */
public record AiHistorySummaryResponse(
        String id,
        String sessionId,
        String userInput,
        String intent,
        String executeResult,
        LocalDateTime createdAt) {
}
