package com.jhun.backend.service.support.ai;

import java.math.BigDecimal;

/**
 * AI Provider 统一结果对象。
 * <p>
 * 当前字段保持与既有 mock provider 落库语义一致，确保本次抽象化不会改变 `/api/ai/chat` 的响应契约和 `chat_history` 写入行为；
 * 后续接入 Qwen provider 时，也必须继续复用这些字段语义，而不是并行引入第二套结果结构。
 *
 * @param intent 识别出的意图编码，当前沿用固定五类意图名称
 * @param intentConfidence 意图识别置信度，保持与历史字段精度兼容
 * @param extractedInfo 结构化提取结果，当前仍写入 JSON 字符串
 * @param executeResult 执行结果编码，当前沿用 `SUCCESS` / `PENDING` / `FAILED`
 * @param aiResponse 返回给控制器和历史记录的 AI 文本回复
 * @param errorMessage 失败或待补充场景下的错误 / 提示信息
 * @param providerName 实际写入 `chat_history.llm_model` 的 provider 标识
 * @param deviceId provider 已唯一定位出的设备 ID；若仍存在歧义或尚未解析成功则必须为 {@code null}
 * @param reservationId provider 已唯一定位出的预约 ID；若仍存在歧义或尚未解析成功则必须为 {@code null}
 */
public record AiProviderResult(
        String intent,
        BigDecimal intentConfidence,
        String extractedInfo,
        String executeResult,
        String aiResponse,
        String errorMessage,
        String providerName,
        String deviceId,
        String reservationId) {
}
