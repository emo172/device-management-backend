package com.jhun.backend.service.support.speech;

/**
 * 语音 provider 抽象。
 * <p>
 * Task 2 起 provider 骨架只保留转写职责，
 * 先从抽象层删掉历史播报/语音输出入口，避免后续 provider 改造时还要兼容已经废弃的合成签名。
 */
public interface SpeechProvider {

    String providerName();

    SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request);
}
