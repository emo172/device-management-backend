package com.jhun.backend.config.speech;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
