package com.jhun.backend.unit.service.support.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.service.support.speech.AzureSpeechSdkClient;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechSynthesisRequest;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamContainerFormat;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * AzureSpeechSdkClient 单元测试。
 * <p>
 * 该测试不触达真实 Azure，而是把 review 阻塞点钉死在 SDK 包装层：
 * 语言 / voice / 输出格式必须先写入 `SpeechConfig`，再创建 recognizer / synthesizer；
 * 同时要固定走 Ogg/Opus 输入容器，并在成功路径关闭 SDK 结果对象，避免资源泄漏回归。
 */
class AzureSpeechSdkClientTest {

    /**
     * 验证转写链路会先配置语言，再创建 `SpeechRecognizer`，并固定走 `OGG_OPUS` 容器。
     */
    @Test
    void shouldConfigureRecognitionBeforeCreatingRecognizerAndCloseSdkResources() {
        SpeechProperties.AzureProperties azureProperties = createAzureProperties();
        AzureSpeechSdkClient client = new AzureSpeechSdkClient();
        SpeechConfig speechConfig = mock(SpeechConfig.class);
        AudioStreamFormat audioStreamFormat = mock(AudioStreamFormat.class);
        PushAudioInputStream inputStream = mock(PushAudioInputStream.class);
        AudioConfig audioConfig = mock(AudioConfig.class);
        SpeechRecognitionResult recognitionResult = mock(SpeechRecognitionResult.class);
        byte[] audioBytes = "fake-ogg-audio".getBytes(StandardCharsets.UTF_8);
        SpeechTranscriptionRequest request = new SpeechTranscriptionRequest(
                audioBytes,
                "audio/ogg;codecs=opus",
                SpeechContract.LOCALE_ZH_CN);

        when(recognitionResult.getReason()).thenReturn(ResultReason.RecognizedSpeech);
        when(recognitionResult.getText()).thenReturn("帮我预约明天下午两点的会议室");

        try (MockedStatic<SpeechConfig> speechConfigStatic = mockStatic(SpeechConfig.class);
                MockedStatic<AudioStreamFormat> audioStreamFormatStatic = mockStatic(AudioStreamFormat.class);
                MockedStatic<PushAudioInputStream> inputStreamStatic = mockStatic(PushAudioInputStream.class);
                MockedStatic<AudioConfig> audioConfigStatic = mockStatic(AudioConfig.class);
                MockedConstruction<SpeechRecognizer> recognizerConstruction = mockConstruction(
                        SpeechRecognizer.class,
                        (speechRecognizer, context) -> {
                            verify(speechConfig).setSpeechRecognitionLanguage(SpeechContract.LOCALE_ZH_CN);
                            assertThat(context.arguments()).hasSize(2);
                            assertThat(context.arguments().get(0)).isSameAs(speechConfig);
                            assertThat(context.arguments().get(1)).isSameAs(audioConfig);
                            when(speechRecognizer.recognizeOnceAsync())
                                    .thenReturn(CompletableFuture.completedFuture(recognitionResult));
                        })) {
            speechConfigStatic.when(() -> SpeechConfig.fromSubscription("test-key", "eastasia"))
                    .thenReturn(speechConfig);
            audioStreamFormatStatic.when(() -> AudioStreamFormat.getCompressedFormat(AudioStreamContainerFormat.OGG_OPUS))
                    .thenReturn(audioStreamFormat);
            inputStreamStatic.when(() -> PushAudioInputStream.createPushStream(audioStreamFormat))
                    .thenReturn(inputStream);
            audioConfigStatic.when(() -> AudioConfig.fromStreamInput(inputStream)).thenReturn(audioConfig);

            SpeechTranscriptionResult result = client.transcribe(azureProperties, request, SpeechContract.PROVIDER_AZURE);

            assertThat(result).isEqualTo(new SpeechTranscriptionResult(
                    "帮我预约明天下午两点的会议室",
                    SpeechContract.LOCALE_ZH_CN,
                    SpeechContract.PROVIDER_AZURE));
            verify(inputStream).write(audioBytes);
            verify(inputStream).close();
            verify(audioConfig).close();
            verify(recognitionResult).close();
            verify(recognizerConstruction.constructed().getFirst()).close();
            verify(speechConfig).close();
        }
    }

    /**
     * 验证合成链路会先配置语言 / voice / 输出格式，再创建 `SpeechSynthesizer`，并在成功后关闭结果对象。
     */
    @Test
    void shouldConfigureSynthesisBeforeCreatingSynthesizerAndCloseSdkResources() {
        SpeechProperties.AzureProperties azureProperties = createAzureProperties();
        AzureSpeechSdkClient client = new AzureSpeechSdkClient();
        SpeechConfig speechConfig = mock(SpeechConfig.class);
        com.microsoft.cognitiveservices.speech.SpeechSynthesisResult synthesisResult =
                mock(com.microsoft.cognitiveservices.speech.SpeechSynthesisResult.class);
        byte[] audioBytes = "fake-mpeg-audio".getBytes(StandardCharsets.UTF_8);
        SpeechSynthesisRequest request = new SpeechSynthesisRequest(
                "请在明天下午两点准时参加会议",
                SpeechContract.LOCALE_ZH_CN,
                SpeechContract.TTS_OUTPUT_CONTENT_TYPE);

        when(synthesisResult.getReason()).thenReturn(ResultReason.SynthesizingAudioCompleted);
        when(synthesisResult.getAudioData()).thenReturn(audioBytes);

        try (MockedStatic<SpeechConfig> speechConfigStatic = mockStatic(SpeechConfig.class);
                MockedConstruction<SpeechSynthesizer> synthesizerConstruction = mockConstruction(
                        SpeechSynthesizer.class,
                        (speechSynthesizer, context) -> {
                            verify(speechConfig).setSpeechSynthesisLanguage(SpeechContract.LOCALE_ZH_CN);
                            verify(speechConfig).setSpeechSynthesisVoiceName(SpeechContract.TTS_VOICE_NAME);
                            verify(speechConfig)
                                    .setSpeechSynthesisOutputFormat(
                                            SpeechSynthesisOutputFormat.Audio16Khz32KBitRateMonoMp3);
                            assertThat(context.arguments()).hasSize(2);
                            assertThat(context.arguments().getFirst()).isSameAs(speechConfig);
                            assertThat(context.arguments().get(1)).isNull();
                            when(speechSynthesizer.SpeakTextAsync(request.text()))
                                    .thenReturn(CompletableFuture.completedFuture(synthesisResult));
                        })) {
            speechConfigStatic.when(() -> SpeechConfig.fromSubscription("test-key", "eastasia"))
                    .thenReturn(speechConfig);

            SpeechSynthesisResult result = client.synthesize(azureProperties, request, SpeechContract.PROVIDER_AZURE);

            assertThat(result).isEqualTo(new SpeechSynthesisResult(
                    audioBytes,
                    SpeechContract.TTS_OUTPUT_CONTENT_TYPE,
                    SpeechContract.PROVIDER_AZURE));
            verify(synthesisResult).close();
            verify(synthesizerConstruction.constructed().getFirst()).close();
            verify(speechConfig).close();
        }
    }

    private SpeechProperties.AzureProperties createAzureProperties() {
        SpeechProperties.AzureProperties azureProperties = new SpeechProperties.AzureProperties();
        azureProperties.setRegion("eastasia");
        azureProperties.setKey("test-key");
        return azureProperties;
    }
}
