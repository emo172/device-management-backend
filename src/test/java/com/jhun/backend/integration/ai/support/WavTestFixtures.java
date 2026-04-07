package com.jhun.backend.integration.ai.support;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class WavTestFixtures {

    private static final int PCM_FORMAT_CODE = 1;

    private WavTestFixtures() {
    }

    public static byte[] validMono16Bit16KhzWav(int sampleFrames) {
        return wav(16000, 1, 16, PCM_FORMAT_CODE, sampleFrames);
    }

    public static byte[] wav(int sampleRate, int channels, int bitsPerSample, int audioFormatCode, int sampleFrames) {
        int bytesPerSample = Math.max(bitsPerSample / 8, 1);
        int blockAlign = channels * bytesPerSample;
        int dataSize = sampleFrames * blockAlign;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(ascii("RIFF"));
        buffer.putInt(36 + dataSize);
        buffer.put(ascii("WAVE"));
        buffer.put(ascii("fmt "));
        buffer.putInt(16);
        buffer.putShort((short) audioFormatCode);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * blockAlign);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        buffer.put(ascii("data"));
        buffer.putInt(dataSize);
        for (int index = 0; index < sampleFrames * channels; index++) {
            if (bitsPerSample == 16) {
                buffer.putShort((short) (index % Short.MAX_VALUE));
            } else {
                buffer.put((byte) (index % Byte.MAX_VALUE));
            }
        }
        return buffer.array();
    }

    public static byte[] replaceAscii(byte[] source, int offset, String value) {
        byte[] copy = Arrays.copyOf(source, source.length);
        byte[] replacement = ascii(value);
        System.arraycopy(replacement, 0, copy, offset, replacement.length);
        return copy;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
