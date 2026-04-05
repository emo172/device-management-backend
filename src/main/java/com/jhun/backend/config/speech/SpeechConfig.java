package com.jhun.backend.config.speech;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 语音配置注册。
 * <p>
 * 当前仓库的语音能力已经固定为 Azure Speech 后端接入；
 * 这里继续只负责注册 {@link SpeechProperties}，让控制层、服务层与 Azure provider 共享同一配置真相源，
 * 避免凭据键名、功能开关和 provider 选择语义在多个位置各自漂移。
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfig {

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
