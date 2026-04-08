package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.SpeechService;
import com.jhun.backend.service.support.speech.IflytekSpeechProvider;
import com.jhun.backend.service.support.speech.ParsedWavAudio;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.jhun.backend.service.support.speech.WavPcmAudioParser;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音服务实现。
 * <p>
 * Task 6 之后，这里承接 `/api/ai/speech/transcriptions` 的完整正式主链路：
 * 先校验 USER 权限与 `speech.enabled`，再把 PCM-WAV 录音解析成裸 PCM，最后交给讯飞 provider 聚合最终 transcript。
 * 对外响应里的 provider 字段固定暴露为稳定值 `iflytek`，避免把内部 provider/result 命名差异泄漏给前端合同。
 * 同时语音 Provider Bean 现在只会在 `speech.enabled=true` 时才真正装配，
 * 因此这里改为延迟解析 Provider，避免“语音明明关闭，但历史环境变量里残留旧 provider 配置就把整个应用启动打挂”的升级回归。
 */
@Service
public class SpeechServiceImpl implements SpeechService {

    private final SpeechProperties speechProperties;

    private final ObjectProvider<SpeechProvider> speechProviderProvider;

    private final WavPcmAudioParser wavPcmAudioParser;

    public SpeechServiceImpl(
            SpeechProperties speechProperties,
            ObjectProvider<SpeechProvider> speechProviderProvider,
            WavPcmAudioParser wavPcmAudioParser) {
        this.speechProperties = speechProperties;
        this.speechProviderProvider = speechProviderProvider;
        this.wavPcmAudioParser = wavPcmAudioParser;
    }

    @Override
    public AiSpeechTranscriptionResponse transcribe(String userId, String role, MultipartFile file) {
        validateSpeechRole(role);
        ensureSpeechEnabled();
        try {
            ParsedWavAudio parsedWavAudio = validateAndParseTranscriptionFile(file);
            SpeechTranscriptionResult result = resolveSpeechProvider().transcribe(new SpeechTranscriptionRequest(
                    parsedWavAudio.pcmBytes(),
                    parsedWavAudio.contentType(),
                    SpeechContract.LOCALE_ZH_CN));
            return new AiSpeechTranscriptionResponse(
                    result.transcript(),
                    result.locale(),
                    IflytekSpeechProvider.IFLYTEK_PROVIDER);
        } catch (SpeechProviderTimeoutException exception) {
            throw new BusinessException(SpeechContract.TRANSCRIPTION_TIMEOUT_MESSAGE);
        } catch (SpeechProviderException exception) {
            throw new BusinessException(SpeechContract.TRANSCRIPTION_FAILED_MESSAGE);
        } catch (IOException exception) {
            throw new BusinessException("读取语音文件失败");
        }
    }

    /**
     * 在真正进入语音主链路时再解析 Provider Bean。
     * <p>
     * 功能关闭场景下，应用上下文允许完全不创建 `SpeechProvider`；只有当用户真的命中了转写入口，
     * 才要求当前运行时必须存在可工作的 provider 装配结果。
     */
    private SpeechProvider resolveSpeechProvider() {
        SpeechProvider speechProvider = speechProviderProvider.getIfAvailable();
        if (speechProvider == null) {
            throw new IllegalStateException("speech.enabled=true 时必须存在可用的 SpeechProvider Bean");
        }
        return speechProvider;
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
     * 转写入口会先做 multipart 空文件与上传体积兜底，再委托独立 WAV 解析器完成 MIME + RIFF/WAVE + PCM 校验。
     * <p>
     * 这样做的原因是：服务层需要继续掌握“空文件 / 超大文件 / 读取失败”的统一业务语义，
     * 但真正与二进制容器格式相关的知识必须收口到专用组件里，避免 `SpeechServiceImpl` 再次退化成 MIME 字符串硬匹配。
     */
    private ParsedWavAudio validateAndParseTranscriptionFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(SpeechContract.EMPTY_AUDIO_MESSAGE);
        }
        if (file.getSize() > speechProperties.getMaxUploadSizeBytes()) {
            throw new BusinessException(SpeechContract.FILE_TOO_LARGE_MESSAGE);
        }
        return wavPcmAudioParser.parse(file.getContentType(), file.getBytes());
    }
}
