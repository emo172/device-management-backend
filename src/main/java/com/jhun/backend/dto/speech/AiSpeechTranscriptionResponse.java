package com.jhun.backend.dto.speech;

/**
 * AI 语音转写响应。
 * <p>
 * 该 DTO 只承载转写文本、固定 locale 和 provider 标识，明确与现有 `AiChatResponse` 文本聊天响应解耦；
 * 后续若需要更多语音元数据，应继续在独立语音 DTO 中扩展，而不是回写文本聊天契约。
 *
 * @param transcript 转写文本
 * @param locale 固定语音 locale，当前只允许 `zh-CN`
 * @param provider 对外稳定暴露的 provider 标识；当前 `/api/ai/speech/transcriptions` 固定返回 `iflytek`
 */
public record AiSpeechTranscriptionResponse(String transcript, String locale, String provider) {
}
