package com.jhun.backend.unit.config.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jhun.backend.config.speech.SpeechConfig;
import com.jhun.backend.config.speech.SpeechProperties;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

/**
 * 语音 multipart 配置测试。
 * <p>
 * 该测试用于锁定 `speech.max-upload-size-bytes` 与 servlet multipart 解析器上限的联动，
 * 避免后续只改服务层校验值却忘记同步 parser 级限制，导致请求在进入语音服务前被容器直接拦下。
 */
class SpeechConfigTest {

    /**
     * 验证当语音上传上限高于现有 parser 配置时，会同步抬高 multipart 文件与请求上限。
     */
    @Test
    void shouldRaiseMultipartLimitsToMatchSpeechUploadLimit() {
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setMaxUploadSizeBytes(DataSize.ofMegabytes(15).toBytes());

        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setMaxFileSize(DataSize.ofMegabytes(10));
        multipartProperties.setMaxRequestSize(DataSize.ofMegabytes(20));

        MultipartConfigElement multipartConfigElement = new SpeechConfig()
                .speechMultipartConfigElement(speechProperties, multipartProperties);

        assertEquals(DataSize.ofMegabytes(15).toBytes(), multipartConfigElement.getMaxFileSize());
        assertEquals(DataSize.ofMegabytes(30).toBytes(), multipartConfigElement.getMaxRequestSize());
    }

    /**
     * 验证当全局 multipart 上限已经更大时，不会因为语音配置较小而意外收紧其他上传链路。
     */
    @Test
    void shouldKeepExistingLargerMultipartLimits() {
        SpeechProperties speechProperties = new SpeechProperties();
        speechProperties.setMaxUploadSizeBytes(DataSize.ofMegabytes(10).toBytes());

        MultipartProperties multipartProperties = new MultipartProperties();
        multipartProperties.setMaxFileSize(DataSize.ofMegabytes(12));
        multipartProperties.setMaxRequestSize(DataSize.ofMegabytes(24));

        MultipartConfigElement multipartConfigElement = new SpeechConfig()
                .speechMultipartConfigElement(speechProperties, multipartProperties);

        assertEquals(DataSize.ofMegabytes(12).toBytes(), multipartConfigElement.getMaxFileSize());
        assertEquals(DataSize.ofMegabytes(24).toBytes(), multipartConfigElement.getMaxRequestSize());
    }
}
