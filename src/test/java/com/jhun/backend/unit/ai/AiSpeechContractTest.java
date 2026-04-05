package com.jhun.backend.unit.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechSynthesisRequest;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI 语音契约测试。
 * <p>
 * 该测试用于锁定 task 1 要求的固定语音口径，确保后续实现不会悄悄改动 locale、音频类型、DTO 结构或 provider 抽象。
 */
class AiSpeechContractTest {

    @Test
    void shouldExposeFixedSpeechContractConstants() {
        assertEquals(SpeechContract.PROVIDER_AZURE, "azure");
        assertEquals("zh-CN", SpeechContract.LOCALE_ZH_CN);
        assertIterableEquals(List.of("audio/ogg", "audio/ogg;codecs=opus"), SpeechContract.SUPPORTED_INPUT_CONTENT_TYPES);
        assertEquals("audio/mpeg", SpeechContract.TTS_OUTPUT_CONTENT_TYPE);
        assertEquals("zh-CN-XiaoxiaoNeural", SpeechContract.TTS_VOICE_NAME);
        assertEquals("语音功能未开启", SpeechContract.FEATURE_DISABLED_MESSAGE);
    }

    @Test
    void shouldDefaultSpeechPropertiesToDisabledAzureBaseline() {
        SpeechProperties properties = new SpeechProperties();

        assertFalse(properties.isEnabled());
        assertEquals(SpeechContract.PROVIDER_AZURE, properties.getProvider());
    }

    @Test
    void shouldKeepSpeechTranscriptionResponseShapeStable() {
        assertArrayEquals(
                new String[] {"transcript", "locale", "provider"},
                Arrays.stream(AiSpeechTranscriptionResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));
    }

    @Test
    void shouldExposeIndependentSpeechProviderContract() throws Exception {
        Method transcribe = SpeechProvider.class.getDeclaredMethod("transcribe", SpeechTranscriptionRequest.class);
        Method synthesize = SpeechProvider.class.getDeclaredMethod("synthesize", SpeechSynthesisRequest.class);

        assertTrue(SpeechProvider.class.isInterface());
        assertEquals(String.class, SpeechProvider.class.getDeclaredMethod("providerName").getReturnType());
        assertEquals(SpeechTranscriptionResult.class, transcribe.getReturnType());
        assertEquals(SpeechSynthesisResult.class, synthesize.getReturnType());
    }
}
