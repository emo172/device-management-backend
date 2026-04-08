package com.jhun.backend.service.support.speech;

import com.jhun.backend.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * PCM-WAV 解析与校验组件。
 * <p>
 * 前端正式上传合同虽然冻结为 `audio/wav`，但浏览器和部分客户端在 multipart 头里仍可能发送
 * `audio/x-wav` / `audio/wave` 等等同变体；本组件会先把这些 HTTP 头收敛到“只接受 WAV 容器”，
 * 再继续严格解析 RIFF/WAVE 二进制头，确保真正送给后续 provider 的一定是：
 * 1) 经典 PCM（format code = 1）；
 * 2) 16kHz / 16bit / 单声道；
 * 3) 不包含任何 WAV 容器头，只保留裸 PCM 数据。
 */
@Component
public class WavPcmAudioParser {

    public static final String PROVIDER_PCM_CONTENT_TYPE = "audio/L16;rate=16000";

    private static final String RIFF_CHUNK_ID = "RIFF";

    private static final String WAVE_FORMAT = "WAVE";

    private static final String FORMAT_CHUNK_ID = "fmt ";

    private static final String DATA_CHUNK_ID = "data";

    private static final int RIFF_HEADER_BYTES = 12;

    private static final int CHUNK_HEADER_BYTES = 8;

    private static final int PCM_FORMAT_CODE = 1;

    private static final int MIN_FORMAT_CHUNK_BYTES = 16;

    private static final Set<String> SUPPORTED_UPLOAD_SUBTYPES = Set.of("wav", "x-wav", "wave");

    /**
     * 校验浏览器上传的 WAV，并剥离为裸 PCM。
     *
     * @param uploadContentType multipart 中携带的上传 MIME
     * @param wavBytes 浏览器上传的 WAV 容器字节
     * @return 供后续 provider 消费的裸 PCM 载荷
     */
    public ParsedWavAudio parse(String uploadContentType, byte[] wavBytes) {
        validateUploadContentType(uploadContentType);
        if (wavBytes == null || wavBytes.length == 0) {
            throw new BusinessException(SpeechContract.EMPTY_AUDIO_MESSAGE);
        }
        if (wavBytes.length < RIFF_HEADER_BYTES) {
            throw unsupportedWavFormat();
        }

        validateRiffWaveHeader(wavBytes);

        WavFormat format = null;
        int offset = RIFF_HEADER_BYTES;
        while (offset <= wavBytes.length - CHUNK_HEADER_BYTES) {
            String chunkId = readAscii(wavBytes, offset, 4);
            long chunkSize = readUnsignedIntLittleEndian(wavBytes, offset + 4);
            if (chunkSize > Integer.MAX_VALUE) {
                throw unsupportedWavFormat();
            }

            int chunkDataOffset = offset + CHUNK_HEADER_BYTES;
            int chunkSizeInt = (int) chunkSize;
            long paddedChunkEnd = (long) chunkDataOffset + chunkSizeInt + (chunkSizeInt % 2L);
            if (paddedChunkEnd > wavBytes.length) {
                throw unsupportedWavFormat();
            }

            if (FORMAT_CHUNK_ID.equals(chunkId)) {
                format = parseFormatChunk(wavBytes, chunkDataOffset, chunkSizeInt);
            } else if (DATA_CHUNK_ID.equals(chunkId)) {
                if (format == null) {
                    /*
                     * 当前实现要求先看到 `fmt ` 再处理 `data`，因为只有拿到 block align / bit depth
                     * 才能安全判断采样帧数和 60 秒上限；若顺序被打乱，宁可稳定拒绝，也不猜测容器含义。
                     */
                    throw unsupportedWavFormat();
                }
                return parseDataChunk(wavBytes, chunkDataOffset, chunkSizeInt, format);
            }

            /*
             * WAV 允许存在 `LIST`、`JUNK` 等非音频 chunk，解析器必须按 chunk size + 奇数字节补齐跳过，
             * 否则会把后续 `fmt ` / `data` 边界读错，最终把合法文件误判成坏头。
             */
            offset = (int) paddedChunkEnd;
        }

        throw unsupportedWavFormat();
    }

    private void validateUploadContentType(String uploadContentType) {
        if (!StringUtils.hasText(uploadContentType)) {
            throw unsupportedWavFormat();
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(uploadContentType);
            if (mediaType.isWildcardType() || mediaType.isWildcardSubtype()) {
                throw unsupportedWavFormat();
            }
            if (!"audio".equalsIgnoreCase(mediaType.getType())) {
                throw unsupportedWavFormat();
            }
            String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_UPLOAD_SUBTYPES.contains(subtype)) {
                throw unsupportedWavFormat();
            }
        } catch (InvalidMediaTypeException exception) {
            throw unsupportedWavFormat();
        }
    }

    /**
     * RIFF/WAVE 头部至少要满足：`RIFF` + 合法长度 + `WAVE`。
     * <p>
     * 不额外强求声明长度与实际长度完全一致，是为了兼容尾部存在额外填充字节的常见编码器；
     * 但若声明长度已经越过实际数组边界，则必须视为损坏容器立即拒绝。
     */
    private void validateRiffWaveHeader(byte[] wavBytes) {
        if (!RIFF_CHUNK_ID.equals(readAscii(wavBytes, 0, 4)) || !WAVE_FORMAT.equals(readAscii(wavBytes, 8, 4))) {
            throw unsupportedWavFormat();
        }
        long declaredRiffSize = readUnsignedIntLittleEndian(wavBytes, 4);
        if (declaredRiffSize < 4 || declaredRiffSize + 8 > wavBytes.length) {
            throw unsupportedWavFormat();
        }
    }

    /**
     * `fmt ` chunk 当前只接受经典 PCM 头。
     * <p>
     * 这里故意不放宽到浮点 PCM、WAVE_FORMAT_EXTENSIBLE 等变体，
     * 因为本期合同已经明确冻结为 16k / 16bit / 单声道 PCM，放宽只会把后续 provider 适配复杂度前置扩散。
     */
    private WavFormat parseFormatChunk(byte[] wavBytes, int chunkOffset, int chunkSize) {
        if (chunkSize < MIN_FORMAT_CHUNK_BYTES) {
            throw unsupportedWavFormat();
        }

        int audioFormatCode = readUnsignedShortLittleEndian(wavBytes, chunkOffset);
        int channels = readUnsignedShortLittleEndian(wavBytes, chunkOffset + 2);
        long sampleRate = readUnsignedIntLittleEndian(wavBytes, chunkOffset + 4);
        long byteRate = readUnsignedIntLittleEndian(wavBytes, chunkOffset + 8);
        int blockAlign = readUnsignedShortLittleEndian(wavBytes, chunkOffset + 12);
        int bitsPerSample = readUnsignedShortLittleEndian(wavBytes, chunkOffset + 14);

        int expectedBlockAlign = SpeechContract.INPUT_CHANNELS * (SpeechContract.INPUT_BITS_PER_SAMPLE / 8);
        long expectedByteRate = (long) SpeechContract.INPUT_SAMPLE_RATE_HZ * expectedBlockAlign;
        if (audioFormatCode != PCM_FORMAT_CODE
                || channels != SpeechContract.INPUT_CHANNELS
                || sampleRate != SpeechContract.INPUT_SAMPLE_RATE_HZ
                || bitsPerSample != SpeechContract.INPUT_BITS_PER_SAMPLE
                || blockAlign != expectedBlockAlign
                || byteRate != expectedByteRate) {
            throw unsupportedWavFormat();
        }

        return new WavFormat(blockAlign);
    }

    private ParsedWavAudio parseDataChunk(byte[] wavBytes, int chunkOffset, int chunkSize, WavFormat format) {
        if (chunkSize == 0) {
            throw new BusinessException(SpeechContract.EMPTY_AUDIO_MESSAGE);
        }
        if (chunkSize % format.blockAlign() != 0) {
            throw unsupportedWavFormat();
        }

        long sampleFrames = chunkSize / format.blockAlign();
        long maxSampleFrames = (long) SpeechContract.INPUT_SAMPLE_RATE_HZ
                * SpeechContract.MAX_RECORDING_DURATION_SECONDS;
        if (sampleFrames > maxSampleFrames) {
            throw new BusinessException(SpeechContract.RECORDING_TOO_LONG_MESSAGE);
        }

        return new ParsedWavAudio(
                Arrays.copyOfRange(wavBytes, chunkOffset, chunkOffset + chunkSize),
                PROVIDER_PCM_CONTENT_TYPE,
                sampleFrames);
    }

    private BusinessException unsupportedWavFormat() {
        return new BusinessException(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE);
    }

    private String readAscii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private int readUnsignedShortLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private long readUnsignedIntLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private record WavFormat(int blockAlign) {
    }
}
