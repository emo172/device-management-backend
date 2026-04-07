package com.jhun.backend.service.support.speech;

import com.jhun.backend.config.speech.SpeechProperties;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class IflytekSpeechProvider implements SpeechProvider {

    public static final String IFLYTEK_PROVIDER = "iflytek";

    private final SpeechProperties speechProperties;

    private final IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient;

    public IflytekSpeechProvider(
            SpeechProperties speechProperties,
            IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient) {
        this.speechProperties = Objects.requireNonNull(speechProperties, "语音配置不能为空");
        this.iflytekSpeechWebSocketClient = Objects.requireNonNull(iflytekSpeechWebSocketClient, "讯飞 WebSocket 客户端不能为空");
    }

    @Override
    public String providerName() {
        validateIflytekProviderSelected();
        return IFLYTEK_PROVIDER;
    }

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        validateIflytekProviderSelected();
        validateIflytekCredentialsConfigured();
        validateRawPcmRequest(request);
        try {
            return iflytekSpeechWebSocketClient.transcribe(
                    speechProperties.getIflytek(),
                    request,
                    IFLYTEK_PROVIDER);
        } catch (IflytekSpeechWebSocketClient.TimeoutException exception) {
            throw new SpeechProviderTimeoutException(exception.getMessage(), exception);
        } catch (IflytekSpeechWebSocketClient.FailureException exception) {
            throw new SpeechProviderException(exception.getMessage(), exception);
        }
    }

    private void validateIflytekProviderSelected() {
        if (!IFLYTEK_PROVIDER.equalsIgnoreCase(speechProperties.getProvider())) {
            throw new SpeechProviderException("当前仅支持 Iflytek Speech provider");
        }
    }

    private void validateIflytekCredentialsConfigured() {
        SpeechProperties.IflytekProperties iflytekProperties = speechProperties.getIflytek();
        if (iflytekProperties == null
                || !StringUtils.hasText(iflytekProperties.getAppId())
                || !StringUtils.hasText(iflytekProperties.getApiKey())
                || !StringUtils.hasText(iflytekProperties.getApiSecret())) {
            throw new SpeechProviderException(
                    "讯飞语音配置不完整，请检查 speech.iflytek.app-id / speech.iflytek.api-key / speech.iflytek.api-secret");
        }
    }

    private void validateRawPcmRequest(SpeechTranscriptionRequest request) {
        if (request == null || request.audioBytes() == null || request.audioBytes().length == 0) {
            throw new SpeechProviderException("讯飞语音转写请求不能为空");
        }
        if (!WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE.equalsIgnoreCase(request.contentType())) {
            throw new SpeechProviderException("讯飞语音转写仅支持 audio/L16;rate=16000 原始 PCM");
        }
    }
}
