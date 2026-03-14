package com.jhun.backend.dto.ai;

import java.time.LocalDateTime;

/**
 * AI 历史详情响应。
 * <p>
 * 当前详情页需要回显用户输入、AI 输出、意图、结构化结果和执行状态，供前端后续扩展历史详情抽屉。
 *
 * @param id 历史记录 ID
 * @param sessionId 会话 ID
 * @param userInput 用户输入
 * @param aiResponse AI 回复
 * @param intent 识别意图
 * @param extractedInfo 提取出的结构化信息 JSON
 * @param executeResult 执行结果
 * @param errorMessage 错误信息
 * @param llmModel provider 标识
 * @param responseTimeMs 响应耗时
 * @param createdAt 创建时间
 */
public record AiHistoryDetailResponse(
        String id,
        String sessionId,
        String userInput,
        String aiResponse,
        String intent,
        String extractedInfo,
        String executeResult,
        String errorMessage,
        String llmModel,
        Integer responseTimeMs,
        LocalDateTime createdAt) {
}
