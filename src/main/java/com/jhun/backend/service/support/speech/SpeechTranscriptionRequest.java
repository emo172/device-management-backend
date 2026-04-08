package com.jhun.backend.service.support.speech;

/**
 * 语音转写请求。
 *
 * @param audioBytes 供 provider 直接消费的音频字节内容；task 3 之后这里固定承载已剥离 WAV 头的裸 PCM
 * @param contentType 供 provider 判定载荷语义的内部内容类型，而非浏览器原始上传 MIME
 * @param locale 固定语音 locale
 */
public record SpeechTranscriptionRequest(byte[] audioBytes, String contentType, String locale) {
}
