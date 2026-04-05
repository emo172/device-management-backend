package com.jhun.backend.service.support.speech;

/**
 * 语音合成请求。
 *
 * @param text 待合成文本
 * @param locale 固定语音 locale
 * @param outputFormat 固定输出格式
 */
public record SpeechSynthesisRequest(String text, String locale, String outputFormat) {
}
