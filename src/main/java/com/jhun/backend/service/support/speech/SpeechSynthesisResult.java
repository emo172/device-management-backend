package com.jhun.backend.service.support.speech;

/**
 * 语音合成结果。
 *
 * @param audioBytes 语音字节流
 * @param contentType 音频响应类型
 * @param provider 实际处理的 provider
 */
public record SpeechSynthesisResult(byte[] audioBytes, String contentType, String provider) {
}
