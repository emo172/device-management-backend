package com.jhun.backend.config.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AI 配置装配入口。
 * <p>
 * 该配置类把 `ai.*` 原始绑定与 `AiRuntimeProperties` 归一化运行时集中收口在同一处，
 * 避免服务层、控制器层或后续 Provider 在多个位置各自解析配置，导致开关语义与 Provider 选择边界不一致。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    public AiRuntimeProperties aiRuntimeProperties(AiProperties aiProperties) {
        return new AiRuntimeProperties(aiProperties);
    }

    /**
     * 为 AI 工具执行层与 Qwen 兼容客户端提供本地 JSON 映射器。
     * <p>
     * 当前 `AiToolExecutionService` 与 Qwen 相关组件都会直接从 Spring 容器注入 `ObjectMapper`，
     * 但 AI 相关测试上下文未必会命中 Web 层 Jackson 自动装配。
     * 因此这里在 AI 配置域内显式补齐一个本地 Bean，确保工具结果序列化、工具参数解析
     * 以及 Qwen OpenAI 兼容协议报文转换都能稳定拿到统一 JSON 能力。
     * 同时自动注册时间等常用模块，避免 AI 返回结构里出现时间字段时再次暴露序列化缺口。
     *
     * @return 供 AI 配置域复用的 JSON 映射器
     */
    @Bean
    public ObjectMapper aiObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    /**
     * 仅在 `ai.provider=qwen` 时注册 Qwen OpenAI 兼容客户端。
     * <p>
     * mock 模式下不应强制要求 qwen 连接信息存在，因此这里使用条件装配把真实上游客户端与 mock 运行时隔离开，
     * 避免本地回退或对照测试时因为缺少 Qwen 配置而无意义失败。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "qwen")
    public QwenOpenAiClient qwenOpenAiClient(ObjectMapper aiObjectMapper, AiRuntimeProperties aiRuntimeProperties) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        QwenOpenAiClient.configureRestClientBuilder(restClientBuilder, aiRuntimeProperties);
        return new QwenOpenAiClient(restClientBuilder.build(), aiObjectMapper, aiRuntimeProperties.qwen());
    }
}
