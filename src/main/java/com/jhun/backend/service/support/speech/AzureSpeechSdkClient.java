package com.jhun.backend.service.support.speech;

import com.jhun.backend.config.speech.SpeechProperties;
import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationErrorCode;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamContainerFormat;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;

/**
 * Azure Speech SDK 调用封装。
 * <p>
 * 该 helper 只承担官方 SDK 交互细节：
 * 1) 用 subscription key + region 创建 `SpeechConfig`；
 * 2) 通过 `OGG_OPUS` 压缩输入流接收浏览器协商后的 `audio/ogg`（Opus）录音；
 * 3) 将 Azure 的识别/合成结果与 cancellation 细节归一化成 provider 可处理的内部异常。
 * 这样 `BaselineSpeechProvider` 仍能专注在配置守卫和异常模型翻译上，而不会被 SDK 细节淹没。
 */
@Component
public class AzureSpeechSdkClient {

    /**
     * 调用 Azure Speech 单次转写。
     * <p>
     * Azure Java SDK 会在构造 `SpeechRecognizer` 时捕获 `SpeechConfig` 当前值，
     * 因此语言配置必须先写入 `SpeechConfig`，再创建 recognizer；
     * 同时这里固定使用 `OGG_OPUS` 容器，避免把浏览器录音误当成原始 PCM 或模糊 `ANY` 路径。
     */
    public SpeechTranscriptionResult transcribe(
            SpeechProperties.AzureProperties azureProperties,
            SpeechTranscriptionRequest request,
            String providerName) {
        Objects.requireNonNull(azureProperties, "Azure Speech 配置不能为空");
        Objects.requireNonNull(request, "语音转写请求不能为空");

        try (SpeechConfig speechConfig = SpeechConfig.fromSubscription(azureProperties.getKey(), azureProperties.getRegion())) {
            speechConfig.setSpeechRecognitionLanguage(request.locale());
            PushAudioInputStream inputStream = PushAudioInputStream.createPushStream(
                    AudioStreamFormat.getCompressedFormat(AudioStreamContainerFormat.OGG_OPUS));
            boolean inputStreamClosed = false;
            try (AudioConfig audioConfig = AudioConfig.fromStreamInput(inputStream);
                    SpeechRecognizer speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig)) {
                inputStream.write(request.audioBytes());
                inputStream.close();
                inputStreamClosed = true;

                try (SpeechRecognitionResult result = speechRecognizer.recognizeOnceAsync().get()) {
                    return mapTranscriptionResult(result, request.locale(), providerName);
                }
            } finally {
                if (!inputStreamClosed) {
                    inputStream.close();
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FailureException("Azure Speech 转写线程被中断", exception);
        } catch (ExecutionException exception) {
            throw new FailureException("Azure Speech 转写执行失败", exception.getCause() == null ? exception : exception.getCause());
        } catch (FailureException | TimeoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FailureException("Azure Speech 转写调用失败", exception);
        }
    }

    /**
     * 调用 Azure Speech 单次文本合成。
     * <p>
     * v1 固定为中文 voice 与 MP3 输出，不引入下载、缓存或多音色切换语义。
     * `SpeechSynthesizer` 也必须在 voice / format 等配置写入之后再创建，
     * 否则 SDK 可能退回默认 voice 或默认音频格式，与接口固定的 `audio/mpeg` 契约冲突。
     */
    public SpeechSynthesisResult synthesize(
            SpeechProperties.AzureProperties azureProperties,
            SpeechSynthesisRequest request,
            String providerName) {
        Objects.requireNonNull(azureProperties, "Azure Speech 配置不能为空");
        Objects.requireNonNull(request, "语音合成请求不能为空");

        try (SpeechConfig speechConfig = SpeechConfig.fromSubscription(azureProperties.getKey(), azureProperties.getRegion())) {
            speechConfig.setSpeechSynthesisLanguage(request.locale());
            speechConfig.setSpeechSynthesisVoiceName(SpeechContract.TTS_VOICE_NAME);
            speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Audio16Khz32KBitRateMonoMp3);
            try (SpeechSynthesizer speechSynthesizer = new SpeechSynthesizer(speechConfig, (AudioConfig) null);
                    com.microsoft.cognitiveservices.speech.SpeechSynthesisResult result = speechSynthesizer
                            .SpeakTextAsync(request.text())
                            .get()) {
                return mapSynthesisResult(result, providerName);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FailureException("Azure Speech 合成线程被中断", exception);
        } catch (ExecutionException exception) {
            throw new FailureException("Azure Speech 合成执行失败", exception.getCause() == null ? exception : exception.getCause());
        } catch (FailureException | TimeoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FailureException("Azure Speech 合成调用失败", exception);
        }
    }

    private SpeechTranscriptionResult mapTranscriptionResult(
            SpeechRecognitionResult result,
            String locale,
            String providerName) {
        if (result == null) {
            throw new FailureException("Azure Speech 转写返回空结果");
        }
        if (result.getReason() == ResultReason.RecognizedSpeech) {
            String transcript = result.getText();
            if (transcript == null || transcript.isBlank()) {
                throw new FailureException("Azure Speech 转写结果为空");
            }
            return new SpeechTranscriptionResult(transcript.trim(), locale, providerName);
        }
        if (result.getReason() == ResultReason.NoMatch) {
            throw new FailureException("Azure Speech 未识别到有效语音内容");
        }
        if (result.getReason() == ResultReason.Canceled) {
            throw classifyCancellation(CancellationDetails.fromResult(result), "转写");
        }
        throw new FailureException("Azure Speech 转写返回未知状态：" + result.getReason());
    }

    private SpeechSynthesisResult mapSynthesisResult(
            com.microsoft.cognitiveservices.speech.SpeechSynthesisResult result,
            String providerName) {
        if (result == null) {
            throw new FailureException("Azure Speech 合成返回空结果");
        }
        if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
            byte[] audioData = result.getAudioData();
            if (audioData == null || audioData.length == 0) {
                throw new FailureException("Azure Speech 合成未返回音频数据");
            }
            return new SpeechSynthesisResult(audioData, SpeechContract.TTS_OUTPUT_CONTENT_TYPE, providerName);
        }
        if (result.getReason() == ResultReason.Canceled) {
            throw classifyCancellation(SpeechSynthesisCancellationDetails.fromResult(result), "合成");
        }
        throw new FailureException("Azure Speech 合成返回未知状态：" + result.getReason());
    }

    private RuntimeException classifyCancellation(CancellationDetails cancellationDetails, String action) {
        if (cancellationDetails == null) {
            return new FailureException("Azure Speech " + action + "被取消，但缺少取消详情");
        }
        String detailMessage = buildCancellationMessage(cancellationDetails, action);
        if (cancellationDetails.getReason() == CancellationReason.Error
                && isTimeoutError(cancellationDetails.getErrorCode())) {
            return new TimeoutException(detailMessage);
        }
        return new FailureException(detailMessage);
    }

    private RuntimeException classifyCancellation(SpeechSynthesisCancellationDetails cancellationDetails, String action) {
        if (cancellationDetails == null) {
            return new FailureException("Azure Speech " + action + "被取消，但缺少取消详情");
        }
        String detailMessage = buildCancellationMessage(
                cancellationDetails.getReason(),
                cancellationDetails.getErrorCode(),
                cancellationDetails.getErrorDetails(),
                action);
        if (cancellationDetails.getReason() == CancellationReason.Error
                && isTimeoutError(cancellationDetails.getErrorCode())) {
            return new TimeoutException(detailMessage);
        }
        return new FailureException(detailMessage);
    }

    private String buildCancellationMessage(CancellationDetails cancellationDetails, String action) {
        return buildCancellationMessage(
                cancellationDetails.getReason(),
                cancellationDetails.getErrorCode(),
                cancellationDetails.getErrorDetails(),
                action);
    }

    private String buildCancellationMessage(
            CancellationReason cancellationReason,
            CancellationErrorCode errorCode,
            String errorDetails,
            String action) {
        StringBuilder messageBuilder = new StringBuilder("Azure Speech ")
                .append(action)
                .append("被取消：")
                .append(cancellationReason);
        if (errorCode != null) {
            messageBuilder.append(" / ").append(errorCode);
        }
        if (errorDetails != null && !errorDetails.isBlank()) {
            messageBuilder.append(" / ").append(errorDetails.trim());
        }
        return messageBuilder.toString();
    }

    private boolean isTimeoutError(CancellationErrorCode errorCode) {
        return errorCode == CancellationErrorCode.ServiceTimeout
                || errorCode == CancellationErrorCode.ConnectionFailure
                || errorCode == CancellationErrorCode.ServiceUnavailable;
    }

    /**
     * Azure SDK cancellation 被归为“可重试超时”时使用的内部异常。
     */
    public static class TimeoutException extends RuntimeException {

        public TimeoutException(String message) {
            super(message);
        }

        public TimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Azure SDK 普通失败统一走该内部异常，再由 provider 翻译成现有公开异常模型。
     */
    public static class FailureException extends RuntimeException {

        public FailureException(String message) {
            super(message);
        }

        public FailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
