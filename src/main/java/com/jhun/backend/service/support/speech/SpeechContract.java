package com.jhun.backend.service.support.speech;

import java.util.List;

/**
 * 语音固定契约。
 * <p>
 * 这里集中固化 task 1 要求的语音边界：中文 locale、浏览器录音格式、TTS 输出格式与统一 feature flag 错误语义。
 * 后续任务只能在不破坏这些基线的前提下补实现，避免把“预留扩展入口”重新演化成不稳定契约。
 */
public final class SpeechContract {

    public static final String PROVIDER_AZURE = "azure";

    public static final String LOCALE_ZH_CN = "zh-CN";

    /**
     * 浏览器录音正式契约固定为 Ogg/Opus。
     * <p>
     * Azure Speech Java SDK 对 `OGG_OPUS` 有明确容器常量与文档支持；
     * 浏览器侧应先通过 `MediaRecorder.isTypeSupported("audio/ogg;codecs=opus")` 探测，再按该口径上传。
     */
    public static final List<String> SUPPORTED_INPUT_CONTENT_TYPES = List.of("audio/ogg", "audio/ogg;codecs=opus");

    public static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    public static final String TTS_OUTPUT_CONTENT_TYPE = "audio/mpeg";

    public static final String TTS_VOICE_NAME = "zh-CN-XiaoxiaoNeural";

    public static final String FEATURE_DISABLED_MESSAGE = "语音功能未开启";

    public static final String EMPTY_AUDIO_MESSAGE = "请上传语音文件";

    public static final String UNSUPPORTED_CONTENT_TYPE_MESSAGE = "仅支持 audio/ogg（Opus）录音文件";

    public static final String FILE_TOO_LARGE_MESSAGE = "语音文件大小不能超过 10MB";

    public static final String TRANSCRIPTION_TIMEOUT_MESSAGE = "语音转写服务超时，请稍后重试";

    public static final String TRANSCRIPTION_FAILED_MESSAGE = "语音转写失败，请稍后重试";

    public static final String EMPTY_HISTORY_RESPONSE_MESSAGE = "该条 AI 历史没有可播放的回复";

    public static final String SYNTHESIS_FAILED_MESSAGE = "AI 历史语音播放失败，请稍后重试";

    private SpeechContract() {
    }
}
