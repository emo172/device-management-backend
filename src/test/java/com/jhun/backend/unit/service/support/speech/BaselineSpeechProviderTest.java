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
import com.jhun.backend.service.support.speech.AzureSpeechSdkClient;
import com.jhun.backend.service.support.speech.BaselineSpeechProvider;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.service.support.speech.SpeechSynthesisRequest;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * BaselineSpeechProvider 单元测试。
 * <p>
 * 该测试专门防止语音 provider 再次退化为“仅保留 Azure 路径但运行时仍是空壳实现”的状态，
 * 同时锁定 provider 对 Azure 超时/失败的异常翻译职责，避免破坏服务层既有业务错误语义。
 */
class BaselineSpeechProviderTest {

    /**
     * 验证转写成功时会继续走 Azure-only 路径，并把 helper 返回结果原样交回服务层。
     */
    @Test
    void shouldDelegateTranscriptionToAzureSdkClient() {
        SpeechProperties speechProperties = createSpeechProperties();
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-ogg".getBytes(StandardCharsets.UTF_8),
                "audio/ogg;codecs=opus",
                SpeechContract.LOCALE_ZH_CN);
        SpeechTranscriptionResult expected = new SpeechTranscriptionResult(
                "帮我预约明天下午两点的会议室",
                SpeechContract.LOCALE_ZH_CN,
                SpeechContract.PROVIDER_AZURE);
        when(azureSpeechSdkClient.transcribe(any(), eq(request), eq(SpeechContract.PROVIDER_AZURE)))
                .thenReturn(expected);

        SpeechTranscriptionResult result = provider.transcribe(request);

        assertThat(result).isEqualTo(expected);
        verify(azureSpeechSdkClient).transcribe(
                speechProperties.getAzure(),
                request,
                SpeechContract.PROVIDER_AZURE);
    }

    /**
     * 验证合成成功时 provider 会沿用固定中文 TTS 路径，不再直接抛出“尚未接入”占位异常。
     */
    @Test
    void shouldDelegateSynthesisToAzureSdkClient() {
        SpeechProperties speechProperties = createSpeechProperties();
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);
        SpeechSynthesisRequest request = new SpeechSynthesisRequest(
                "请在明天下午两点准时参加会议",
                SpeechContract.LOCALE_ZH_CN,
                SpeechContract.TTS_OUTPUT_CONTENT_TYPE);
        SpeechSynthesisResult expected = new SpeechSynthesisResult(
                "fake-mpeg".getBytes(StandardCharsets.UTF_8),
                SpeechContract.TTS_OUTPUT_CONTENT_TYPE,
                SpeechContract.PROVIDER_AZURE);
        when(azureSpeechSdkClient.synthesize(any(), eq(request), eq(SpeechContract.PROVIDER_AZURE)))
                .thenReturn(expected);

        SpeechSynthesisResult result = provider.synthesize(request);

        assertThat(result).isEqualTo(expected);
        verify(azureSpeechSdkClient).synthesize(
                speechProperties.getAzure(),
                request,
                SpeechContract.PROVIDER_AZURE);
    }

    /**
     * 验证 Azure helper 标记为超时时，provider 会翻译成现有可重试语义使用的超时异常类型。
     */
    @Test
    void shouldTranslateAzureTimeoutIntoSpeechProviderTimeoutException() {
        SpeechProperties speechProperties = createSpeechProperties();
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                "fake-ogg".getBytes(StandardCharsets.UTF_8),
                "audio/ogg",
                SpeechContract.LOCALE_ZH_CN);
        when(azureSpeechSdkClient.transcribe(any(), eq(request), eq(SpeechContract.PROVIDER_AZURE)))
                .thenThrow(new AzureSpeechSdkClient.TimeoutException("Azure Speech 转写超时"));

        assertThatThrownBy(() -> provider.transcribe(request))
                .isInstanceOf(SpeechProviderTimeoutException.class)
                .hasMessageContaining("Azure Speech 转写超时");
    }

    /**
     * 验证 Azure helper 的普通失败会继续映射成通用 provider 异常，供服务层统一翻译业务文案。
     */
    @Test
    void shouldTranslateAzureFailureIntoSpeechProviderException() {
        SpeechProperties speechProperties = createSpeechProperties();
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);
        SpeechSynthesisRequest request = new SpeechSynthesisRequest(
                "这是一段需要播报的回复",
                SpeechContract.LOCALE_ZH_CN,
                SpeechContract.TTS_OUTPUT_CONTENT_TYPE);
        when(azureSpeechSdkClient.synthesize(any(), eq(request), eq(SpeechContract.PROVIDER_AZURE)))
                .thenThrow(new AzureSpeechSdkClient.FailureException("Azure Speech 合成失败"));

        assertThatThrownBy(() -> provider.synthesize(request))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("Azure Speech 合成失败")
                .isNotInstanceOf(SpeechProviderTimeoutException.class);
    }

    /**
     * 验证当前 provider 仍然是 Azure-only：若配置被误改为其他值，要在 provider 边界直接失败，避免制造多供应商已可切换的假象。
     */
    @Test
    void shouldRejectNonAzureProviderConfiguration() {
        SpeechProperties speechProperties = createSpeechProperties();
        speechProperties.setProvider("mock");
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);

        assertThatThrownBy(() -> provider.transcribe(new SpeechTranscriptionRequest(
                        "fake-ogg".getBytes(StandardCharsets.UTF_8),
                        "audio/ogg",
                        SpeechContract.LOCALE_ZH_CN)))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("当前仅支持 Azure Speech provider");

        verifyNoInteractions(azureSpeechSdkClient);
    }

    /**
     * 验证缺少 Azure 凭据时会在 provider 边界直接失败，避免把空配置拖到真实 SDK 调用才以晦涩错误暴露。
     */
    @Test
    void shouldRejectIncompleteAzureCredentials() {
        SpeechProperties speechProperties = new SpeechProperties();
        AzureSpeechSdkClient azureSpeechSdkClient = mock(AzureSpeechSdkClient.class);
        BaselineSpeechProvider provider = new BaselineSpeechProvider(speechProperties, azureSpeechSdkClient);

        assertThatThrownBy(() -> provider.synthesize(new SpeechSynthesisRequest(
                        "这是一段需要播报的回复",
                        SpeechContract.LOCALE_ZH_CN,
                        SpeechContract.TTS_OUTPUT_CONTENT_TYPE)))
                .isInstanceOf(SpeechProviderException.class)
                .hasMessageContaining("speech.azure.region")
                .hasMessageContaining("speech.azure.key");

        verifyNoInteractions(azureSpeechSdkClient);
    }

    private SpeechProperties createSpeechProperties() {
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setProvider(SpeechContract.PROVIDER_AZURE);
        speechProperties.getAzure().setRegion("eastasia");
        speechProperties.getAzure().setKey("test-azure-key");
        return speechProperties;
    }
}
