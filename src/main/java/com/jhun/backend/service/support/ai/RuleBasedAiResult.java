package com.jhun.backend.service.support.ai;

import java.math.BigDecimal;

/**
 * 规则降级 AI 执行结果。
 *
 * @param intent 识别意图
 * @param intentConfidence 识别置信度
 * @param extractedInfo 结构化信息 JSON
 * @param executeResult 执行结果
 * @param aiResponse AI 回复文本
 * @param errorMessage 错误信息
 * @param providerName provider 标识
 */
public record RuleBasedAiResult(
        String intent,
        BigDecimal intentConfidence,
        String extractedInfo,
        String executeResult,
        String aiResponse,
        String errorMessage,
        String providerName) {
}
