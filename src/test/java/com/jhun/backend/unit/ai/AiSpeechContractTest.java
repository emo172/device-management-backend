package com.jhun.backend.unit.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.jhun.backend.dto.ai.AiCapabilitiesResponse;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.support.speech.SpeechContract;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI 语音契约测试。
 * <p>
 * 该测试用于锁定 task 1 要求的固定语音口径，确保后续实现不会悄悄改动
 * `speechEnabled` 的 ASR-only 语义、正式上传格式、录音时长或公开 DTO 结构。
 */
class AiSpeechContractTest {

    @Test
    void shouldExposeFixedAsrOnlySpeechContractConstants() {
        assertEquals("zh-CN", SpeechContract.LOCALE_ZH_CN);
        assertIterableEquals(List.of("audio/wav"), SpeechContract.SUPPORTED_INPUT_CONTENT_TYPES);
        assertEquals(16000, SpeechContract.INPUT_SAMPLE_RATE_HZ);
        assertEquals(16, SpeechContract.INPUT_BITS_PER_SAMPLE);
        assertEquals(1, SpeechContract.INPUT_CHANNELS);
        assertEquals(60, SpeechContract.MAX_RECORDING_DURATION_SECONDS);
        assertEquals("语音输入转写未开启", SpeechContract.FEATURE_DISABLED_MESSAGE);
        assertEquals("仅支持 audio/wav（16k / 16bit / 单声道 PCM）录音文件", SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE);
    }

    @Test
    void shouldNotExposeLegacyAzureOrPlaybackContractFields() {
        List<String> fieldNames = Arrays.stream(SpeechContract.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertFalse(fieldNames.contains("PROVIDER_AZURE"));
        assertFalse(fieldNames.contains("TTS_OUTPUT_CONTENT_TYPE"));
        assertFalse(fieldNames.contains("TTS_VOICE_NAME"));
        assertFalse(fieldNames.contains("EMPTY_HISTORY_RESPONSE_MESSAGE"));
        assertFalse(fieldNames.contains("SYNTHESIS_FAILED_MESSAGE"));
    }

    @Test
    void shouldDefaultSpeechPropertiesToDisabledIflytekOnlyTranscriptionConfig() {
        SpeechProperties properties = new SpeechProperties();
        List<String> fieldNames = Arrays.stream(SpeechProperties.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertFalse(properties.isEnabled());
        assertEquals("iflytek", properties.getProvider());
        assertEquals(SpeechContract.MAX_UPLOAD_SIZE_BYTES, properties.getMaxUploadSizeBytes());
        assertIterableEquals(List.of("enabled", "provider", "iflytek", "maxUploadSizeBytes"), fieldNames);
    }

    @Test
    void shouldKeepAiCapabilitiesResponseShapeStable() {
        assertArrayEquals(
                new String[] {"chatEnabled", "speechEnabled"},
                Arrays.stream(AiCapabilitiesResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));
    }

    @Test
    void shouldKeepSpeechTranscriptionResponseShapeStable() {
        assertArrayEquals(
                new String[] {"transcript", "locale", "provider"},
                Arrays.stream(AiSpeechTranscriptionResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));
    }

}
