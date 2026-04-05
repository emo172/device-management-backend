package com.jhun.backend.service.support.speech;

/**
 * 语音转写请求。
 *
 * @param audioBytes 录音字节内容
 * @param contentType 浏览器上传的音频类型
 * @param locale 固定语音 locale
 */
public record SpeechTranscriptionRequest(byte[] audioBytes, String contentType, String locale) {
}
