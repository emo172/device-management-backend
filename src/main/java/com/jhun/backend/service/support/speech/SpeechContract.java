package com.jhun.backend.service.support.speech;

import java.util.List;

/**
 * 语音输入转写公共合同。
 * <p>
 * 这里集中固化 task 1 要求的 ASR-only 公开边界：
 * 1) `speechEnabled` 只表示语音输入转写是否可用；
 * 2) 正式上传合同固定为 `audio/wav`（16k / 16bit / 单声道 PCM）；
 * 3) 浏览器单次录音时长上限固定为 60 秒；
 * 4) 不在该类中暴露 provider、语音输出或历史播放语义。
 */
public final class SpeechContract {

    public static final String LOCALE_ZH_CN = "zh-CN";

    /**
     * 前后端联调的正式上传 MIME 只认 `audio/wav`。
     * <p>
     * 后续实现需要把 WAV 头剥离为原始 PCM 再接给上游服务，但公共合同在这一层先冻结为稳定的浏览器上传容器，
     * 避免把临时录音格式或供应商 SDK 偏好继续暴露给前端。
     */
    public static final List<String> SUPPORTED_INPUT_CONTENT_TYPES = List.of("audio/wav");

    public static final int INPUT_SAMPLE_RATE_HZ = 16000;

    public static final int INPUT_BITS_PER_SAMPLE = 16;

    public static final int INPUT_CHANNELS = 1;

    public static final int MAX_RECORDING_DURATION_SECONDS = 60;

    public static final long MAX_UPLOAD_SIZE_BYTES = 10L * 1024 * 1024;

    public static final String FEATURE_DISABLED_MESSAGE = "语音输入转写未开启";

    public static final String EMPTY_AUDIO_MESSAGE = "请上传语音文件";

    public static final String UNSUPPORTED_CONTENT_TYPE_MESSAGE = "仅支持 audio/wav（16k / 16bit / 单声道 PCM）录音文件";

    public static final String FILE_TOO_LARGE_MESSAGE = "语音文件大小不能超过 10MB";

    public static final String RECORDING_TOO_LONG_MESSAGE = "语音录音时长不能超过 60 秒";

    public static final String TRANSCRIPTION_TIMEOUT_MESSAGE = "语音转写服务超时，请稍后重试";

    public static final String TRANSCRIPTION_FAILED_MESSAGE = "语音转写失败，请稍后重试";

    private SpeechContract() {
    }
}
