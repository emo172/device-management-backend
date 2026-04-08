package com.jhun.backend.unit.config.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.jhun.backend.config.speech.SpeechConfig;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.service.SpeechService;
import com.jhun.backend.service.impl.SpeechServiceImpl;
import com.jhun.backend.service.support.speech.IflytekSpeechWebSocketClient;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.WavPcmAudioParser;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 语音 multipart 配置测试。
 * <p>
 * 该测试用于锁定 `speech.max-upload-size-bytes` 与 servlet multipart 解析器上限的联动，
 * 避免后续只改服务层校验值却忘记同步 parser 级限制，导致请求在进入语音服务前被容器直接拦下。
 */
class SpeechConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, SpeechConfig.class);

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

    /**
     * 验证语音功能关闭时，就算环境里还残留旧的 provider 配置，也不会因为 `SpeechProvider` Bean 装配失败而拖垮整个应用启动。
     */
    @Test
    void shouldAllowApplicationStartWhenSpeechDisabledButLegacyProviderRemains() {
        contextRunner
                .withPropertyValues(
                        "speech.enabled=false",
                        "speech.provider=azure")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SpeechService.class);
                    assertThat(context).doesNotHaveBean(SpeechProvider.class);
                });
    }

    /**
     * 验证一旦显式开启语音功能，运行时仍然只接受当前唯一正式支持的讯飞 provider。
     */
    @Test
    void shouldFailFastWhenSpeechEnabledUsesUnsupportedProvider() {
        contextRunner
                .withPropertyValues(
                        "speech.enabled=true",
                        "speech.provider=azure")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("当前仅支持 Iflytek 语音 provider：azure");
                });
    }

    /**
     * 只为当前配置测试提供最小 Spring 上下文。
     * <p>
     * 这里故意只注册语音配置、解析器、假的 WebSocket 客户端和 `SpeechService`，
     * 让用例专注验证“关闭时不装配 provider、开启时继续 fail-fast”这两个启动期契约，避免被控制器或安全链噪音干扰。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SpeechProperties.class)
    static class TestConfiguration {

        @Bean
        MultipartProperties multipartProperties() {
            return new MultipartProperties();
        }

        @Bean
        IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient() {
            return mock(IflytekSpeechWebSocketClient.class);
        }

        @Bean
        WavPcmAudioParser wavPcmAudioParser() {
            return new WavPcmAudioParser();
        }

        @Bean
        SpeechService speechService(
                SpeechProperties speechProperties,
                ObjectProvider<SpeechProvider> speechProviderProvider,
                WavPcmAudioParser wavPcmAudioParser) {
            return new SpeechServiceImpl(speechProperties, speechProviderProvider, wavPcmAudioParser);
        }
    }
}
