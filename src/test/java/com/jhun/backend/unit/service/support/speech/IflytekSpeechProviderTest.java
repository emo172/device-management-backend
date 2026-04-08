package com.jhun.backend.unit.service.support.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.service.support.speech.IflytekSpeechProvider;
import com.jhun.backend.service.support.speech.IflytekSpeechWebSocketClient;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.jhun.backend.service.support.speech.WavPcmAudioParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IflytekSpeechProviderTest {

    @Test
    void shouldDelegateTranscriptionToIflytekWebSocketClient() {
        SpeechProperties speechProperties = createSpeechProperties();
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-pcm".getBytes(StandardCharsets.UTF_8),
                WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                "zh-CN");
        SpeechTranscriptionResult expected = new SpeechTranscriptionResult(
                "帮我预约明天下午两点的会议室",
                "zh-CN",
                IflytekSpeechProvider.IFLYTEK_PROVIDER);
        when(iflytekSpeechWebSocketClient.transcribe(any(), eq(request), eq(IflytekSpeechProvider.IFLYTEK_PROVIDER)))
                .thenReturn(expected);

        SpeechTranscriptionResult result = provider.transcribe(request);

        assertThat(result).isEqualTo(expected);
        verify(iflytekSpeechWebSocketClient).transcribe(
                speechProperties.getIflytek(),
                request,
                IflytekSpeechProvider.IFLYTEK_PROVIDER);
    }

    @Test
    void shouldTranslateIflytekAuthenticationFailureIntoSpeechProviderException() {
        SpeechProperties speechProperties = createSpeechProperties();
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-pcm".getBytes(StandardCharsets.UTF_8),
                WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                "zh-CN");
        when(iflytekSpeechWebSocketClient.transcribe(any(), eq(request), eq(IflytekSpeechProvider.IFLYTEK_PROVIDER)))
                .thenThrow(new IflytekSpeechWebSocketClient.AuthenticationException("讯飞语音鉴权失败：签名无效"));

        assertThatThrownBy(() -> provider.transcribe(request))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("鉴权失败")
                .isNotInstanceOf(SpeechProviderTimeoutException.class);
    }

    @Test
    void shouldTranslateIflytekTimeoutIntoSpeechProviderTimeoutException() {
        SpeechProperties speechProperties = createSpeechProperties();
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-pcm".getBytes(StandardCharsets.UTF_8),
                WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                "zh-CN");
        when(iflytekSpeechWebSocketClient.transcribe(any(), eq(request), eq(IflytekSpeechProvider.IFLYTEK_PROVIDER)))
                .thenThrow(new IflytekSpeechWebSocketClient.TimeoutException("讯飞语音会话超时：code=10200"));

        assertThatThrownBy(() -> provider.transcribe(request))
                .isInstanceOf(SpeechProviderTimeoutException.class)
                .hasMessageContaining("会话超时");
    }

    @Test
    void shouldTranslateIflytekBusinessFailureIntoSpeechProviderException() {
        SpeechProperties speechProperties = createSpeechProperties();
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-pcm".getBytes(StandardCharsets.UTF_8),
                WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                "zh-CN");
        when(iflytekSpeechWebSocketClient.transcribe(any(), eq(request), eq(IflytekSpeechProvider.IFLYTEK_PROVIDER)))
                .thenThrow(new IflytekSpeechWebSocketClient.FailureException("讯飞语音转写失败：code=10165"));

        assertThatThrownBy(() -> provider.transcribe(request))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("code=10165")
                .isNotInstanceOf(SpeechProviderTimeoutException.class);
    }

    @Test
    void shouldRejectUnexpectedProviderConfiguration() {
        SpeechProperties speechProperties = createSpeechProperties();
        speechProperties.setProvider("unexpected-provider");
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);

        assertThatThrownBy(() -> provider.transcribe(new SpeechTranscriptionRequest(
                        "fake-pcm".getBytes(StandardCharsets.UTF_8),
                        WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                        "zh-CN")))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("Iflytek Speech provider");

        verifyNoInteractions(iflytekSpeechWebSocketClient);
    }

    @Test
    void shouldTreatBlankProviderAsDefaultIflytekSelection() {
        SpeechProperties speechProperties = createSpeechProperties();
        speechProperties.setProvider("   ");
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-pcm".getBytes(StandardCharsets.UTF_8),
                WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                "zh-CN");
        SpeechTranscriptionResult expected = new SpeechTranscriptionResult(
                "帮我预约明天下午两点的会议室",
                "zh-CN",
                IflytekSpeechProvider.IFLYTEK_PROVIDER);
        when(iflytekSpeechWebSocketClient.transcribe(any(), eq(request), eq(IflytekSpeechProvider.IFLYTEK_PROVIDER)))
                .thenReturn(expected);

        SpeechTranscriptionResult result = provider.transcribe(request);

        assertThat(result).isEqualTo(expected);
        verify(iflytekSpeechWebSocketClient).transcribe(
                speechProperties.getIflytek(),
                request,
                IflytekSpeechProvider.IFLYTEK_PROVIDER);
    }

    @Test
    void shouldRejectIncompleteIflytekCredentials() {
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setProvider(IflytekSpeechProvider.IFLYTEK_PROVIDER);
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);

        assertThatThrownBy(() -> provider.transcribe(new SpeechTranscriptionRequest(
                        "fake-pcm".getBytes(StandardCharsets.UTF_8),
                        WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE,
                        "zh-CN")))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("speech.iflytek.app-id")
                .hasMessageContaining("speech.iflytek.api-key")
                .hasMessageContaining("speech.iflytek.api-secret");

        verifyNoInteractions(iflytekSpeechWebSocketClient);
    }

    @Test
    void shouldRejectNonRawPcmPayload() {
        SpeechProperties speechProperties = createSpeechProperties();
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient = mock(IflytekSpeechWebSocketClient.class);
        IflytekSpeechProvider provider = new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);

        assertThatThrownBy(() -> provider.transcribe(new SpeechTranscriptionRequest(
                        "fake-wav".getBytes(StandardCharsets.UTF_8),
                        "audio/wav",
                        "zh-CN")))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("audio/L16;rate=16000");

        verifyNoInteractions(iflytekSpeechWebSocketClient);
    }

    private SpeechProperties createSpeechProperties() {
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setProvider(IflytekSpeechProvider.IFLYTEK_PROVIDER);
        speechProperties.getIflytek().setAppId("test-app-id");
        speechProperties.getIflytek().setApiKey("test-api-key");
        speechProperties.getIflytek().setApiSecret("test-api-secret");
        return speechProperties;
    }
}
