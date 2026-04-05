package com.jhun.backend.service.support.speech;

/**
 * 语音转写结果。
 *
 * @param transcript 转写文本
 * @param locale 固定语音 locale
 * @param provider 实际处理的 provider
 */
public record SpeechTranscriptionResult(String transcript, String locale, String provider) {
}
