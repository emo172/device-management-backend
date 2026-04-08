package com.jhun.backend.config.speech;

import com.jhun.backend.service.support.speech.IflytekSpeechProvider;
import com.jhun.backend.service.support.speech.IflytekSpeechWebSocketClient;
import com.jhun.backend.service.support.speech.SpeechProvider;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfig {

    @Bean
    @ConditionalOnProperty(prefix = "speech", name = "enabled", havingValue = "true")
    public SpeechProvider speechProvider(
            SpeechProperties speechProperties,
            IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient) {
        if (!speechProperties.isIflytekProviderSelected()) {
            throw new IllegalStateException("当前仅支持 Iflytek 语音 provider：" + speechProperties.getProvider());
        }
        return new IflytekSpeechProvider(speechProperties, iflytekSpeechWebSocketClient);
    }

    /**
     * 将语音上传上限同步到 servlet multipart 解析器。
     * <p>
     * `speech.max-upload-size-bytes` 是语音链路的业务真相源；若只改服务层校验而不抬高解析器上限，
     * 请求会先在 multipart 解析阶段被拒绝，导致调用方拿不到语音域统一业务错误。
     * 这里保留现有全局 multipart 配置作为基础下限，再按语音上传上限抬高文件与请求大小，
     * 避免其他上传链路被意外收紧，同时保证语音链路的配置项真实生效。
     */
    @Bean
    public MultipartConfigElement speechMultipartConfigElement(
            SpeechProperties speechProperties,
            MultipartProperties multipartProperties) {
        MultipartConfigFactory multipartConfigFactory = new MultipartConfigFactory();
        multipartConfigFactory.setLocation(multipartProperties.getLocation());
        multipartConfigFactory.setFileSizeThreshold(multipartProperties.getFileSizeThreshold());
        multipartConfigFactory.setMaxFileSize(maxDataSize(
                multipartProperties.getMaxFileSize(),
                DataSize.ofBytes(speechProperties.getMaxUploadSizeBytes())));
        multipartConfigFactory.setMaxRequestSize(maxDataSize(
                multipartProperties.getMaxRequestSize(),
                DataSize.ofBytes(speechProperties.getMaxUploadSizeBytes() * 2)));
        return multipartConfigFactory.createMultipartConfig();
    }

    private DataSize maxDataSize(DataSize left, DataSize right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.toBytes() >= right.toBytes() ? left : right;
    }

}
