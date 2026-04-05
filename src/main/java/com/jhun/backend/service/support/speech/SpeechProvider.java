package com.jhun.backend.service.support.speech;

/**
 * 语音 provider 抽象。
 * <p>
 * 该接口独立于现有文本 AI provider，专门承接录音转写和文本转语音两类能力，
 * 防止后续把音频能力塞回文本 AI 对话链路。
 */
public interface SpeechProvider {

    String providerName();

    SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request);

    SpeechSynthesisResult synthesize(SpeechSynthesisRequest request);
}
