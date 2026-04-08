package com.jhun.backend.service;

import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音服务。
 * <p>
 * Task 2 起该服务骨架正式收口为“仅负责录音转写”。
 * 这样可以先把历史播报链路从 service facade 上彻底切断，
 * 避免后续讯飞 ASR 接入期间继续暴露已经判定要删除的语音播放契约。
 */
public interface SpeechService {

    AiSpeechTranscriptionResponse transcribe(String userId, String role, MultipartFile file);
}
