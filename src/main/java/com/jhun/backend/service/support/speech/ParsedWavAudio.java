package com.jhun.backend.service.support.speech;

/**
 * WAV 解析后的裸 PCM 载荷。
 *
 * @param pcmBytes 已剥离容器头与附加 chunk 的裸 PCM 字节
 * @param contentType 供后续 provider 消费的内部音频类型，明确表示当前载荷不再是 WAV 容器
 * @param sampleFrames 按 block align 折算后的采样帧数，用于锁定 60 秒边界与后续分帧基线
 */
public record ParsedWavAudio(byte[] pcmBytes, String contentType, long sampleFrames) {
}
