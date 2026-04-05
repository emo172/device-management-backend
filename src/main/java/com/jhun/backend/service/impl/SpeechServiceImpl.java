package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.entity.ChatHistory;
import com.jhun.backend.mapper.ChatHistoryMapper;
import com.jhun.backend.service.SpeechService;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.service.support.speech.SpeechSynthesisRequest;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音服务实现。
 * <p>
 * task 2 在 task 1 基线之上补齐转写最小闭环：
 * 既要复用 `/api/ai` 的 USER 权限边界，也要在服务层显式校验录音格式与大小，
 * 同时把 provider 超时/失败翻译成稳定业务错误，避免控制层或底层 SDK 直接暴露实现细节。
 */
@Service
public class SpeechServiceImpl implements SpeechService {

    private final ChatHistoryMapper chatHistoryMapper;

    private final SpeechProperties speechProperties;

    private final SpeechProvider speechProvider;

    public SpeechServiceImpl(
            ChatHistoryMapper chatHistoryMapper,
            SpeechProperties speechProperties,
            SpeechProvider speechProvider) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.speechProperties = speechProperties;
        this.speechProvider = speechProvider;
    }

    @Override
    public AiSpeechTranscriptionResponse transcribe(String userId, String role, MultipartFile file) {
        validateSpeechRole(role);
        ensureSpeechEnabled();
        validateTranscriptionFile(file);
        try {
            SpeechTranscriptionResult result = speechProvider.transcribe(new SpeechTranscriptionRequest(
                    file.getBytes(),
                    file.getContentType(),
                    SpeechContract.LOCALE_ZH_CN));
            return new AiSpeechTranscriptionResponse(result.transcript(), result.locale(), result.provider());
        } catch (SpeechProviderTimeoutException exception) {
            throw new BusinessException(SpeechContract.TRANSCRIPTION_TIMEOUT_MESSAGE);
        } catch (SpeechProviderException exception) {
            throw new BusinessException(SpeechContract.TRANSCRIPTION_FAILED_MESSAGE);
        } catch (IOException exception) {
            throw new BusinessException("读取语音文件失败");
        }
    }

    @Override
    public SpeechSynthesisResult synthesizeHistorySpeech(String userId, String role, String historyId) {
        validateSpeechRole(role);
        ensureSpeechEnabled();
        ChatHistory history = chatHistoryMapper.findOwnedById(userId, historyId);
        if (history == null) {
            throw new BusinessException("AI 对话历史不存在");
        }
        if (history.getAiResponse() == null || history.getAiResponse().isBlank()) {
            throw new BusinessException(SpeechContract.EMPTY_HISTORY_RESPONSE_MESSAGE);
        }
        try {
            SpeechSynthesisResult result = speechProvider.synthesize(new SpeechSynthesisRequest(
                    history.getAiResponse().trim(),
                    SpeechContract.LOCALE_ZH_CN,
                    SpeechContract.TTS_OUTPUT_CONTENT_TYPE));
            if (result == null || result.audioBytes() == null || result.audioBytes().length == 0) {
                throw new BusinessException(SpeechContract.SYNTHESIS_FAILED_MESSAGE);
            }
            return new SpeechSynthesisResult(
                    result.audioBytes(),
                    SpeechContract.TTS_OUTPUT_CONTENT_TYPE,
                    result.provider());
        } catch (SpeechProviderTimeoutException exception) {
            throw new BusinessException(SpeechContract.SYNTHESIS_FAILED_MESSAGE);
        } catch (SpeechProviderException exception) {
            throw new BusinessException(SpeechContract.SYNTHESIS_FAILED_MESSAGE);
        }
    }

    private void ensureSpeechEnabled() {
        if (!speechProperties.isEnabled()) {
            throw new BusinessException(SpeechContract.FEATURE_DISABLED_MESSAGE);
        }
    }

    private void validateSpeechRole(String role) {
        if (!"USER".equals(role)) {
            throw new BusinessException("只有普通用户可以使用 AI 语音功能");
        }
    }

    /**
     * 转写入口只接受浏览器录音 `audio/webm`，并限制在 10MB 以内。
     * <p>
     * 这里故意在服务层做二次显式校验，而不是完全依赖 multipart 基础设施，
     * 这样可以稳定返回统一业务错误，同时保证失败路径不会创建本地临时文件或遗留磁盘副本。
     */
    private void validateTranscriptionFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(SpeechContract.EMPTY_AUDIO_MESSAGE);
        }
        if (!SpeechContract.SUPPORTED_INPUT_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE);
        }
        if (file.getSize() > speechProperties.getMaxUploadSizeBytes()) {
            throw new BusinessException(SpeechContract.FILE_TOO_LARGE_MESSAGE);
        }
    }
}
