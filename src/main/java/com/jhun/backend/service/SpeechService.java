package com.jhun.backend.service;

import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音服务。
 * <p>
 * 该服务只负责承接 AI 语音接口的权限边界、功能开关与 provider 编排，
 * 不得改造现有文本 AI 请求响应对象，也不得让语音逻辑反向侵入文本聊天链路。
 */
public interface SpeechService {

    AiSpeechTranscriptionResponse transcribe(String userId, String role, MultipartFile file);

    SpeechSynthesisResult synthesizeHistorySpeech(String userId, String role, String historyId);
}
