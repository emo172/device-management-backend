package com.jhun.backend.config.ai;

import java.time.Duration;
import java.util.Objects;

/**
 * AI 运行时归一化配置。
 * <p>
 * 该对象把原始绑定结果转换成服务层和后续 Provider 可直接消费的运行时语义，统一负责：
 * 1) 把超时秒数归一化为 `Duration`；
 * 2) 把 Provider 选择收敛到 `mock` / `qwen` 两种正式状态；
 * 3) 在选中 `qwen` 时对必需配置执行 fail-fast 校验，避免错误拖延到首条请求。
 */
public final class AiRuntimeProperties {

    private final boolean chatEnabled;
    private final AiProperties.Provider provider;
    private final Duration requestTimeout;
    private final QwenRuntimeProperties qwen;

    public AiRuntimeProperties(AiProperties aiProperties) {
        Objects.requireNonNull(aiProperties, "ai 配置不能为空");
        this.chatEnabled = aiProperties.enabled();
        this.provider = requireProperty(aiProperties.provider(), "ai.provider");
        this.requestTimeout = resolveTimeout(aiProperties.timeoutSeconds());
        this.qwen = resolveQwenRuntime(this.provider, aiProperties.qwen());
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public AiProperties.Provider provider() {
        return provider;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public QwenRuntimeProperties qwen() {
        return qwen;
    }

    private Duration resolveTimeout(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("ai.timeout-seconds 必须大于 0");
        }
        return Duration.ofSeconds(timeoutSeconds);
    }

    private QwenRuntimeProperties resolveQwenRuntime(AiProperties.Provider configuredProvider, AiProperties.Qwen qwenProperties) {
        if (!AiProperties.Provider.QWEN.equals(configuredProvider)) {
            return null;
        }
        if (qwenProperties == null) {
            throw new IllegalStateException("当 ai.provider=qwen 时必须配置 ai.qwen");
        }
        return new QwenRuntimeProperties(
                requireText(qwenProperties.baseUrl(), "ai.qwen.base-url"),
                requireText(qwenProperties.model(), "ai.qwen.model"),
                requireText(qwenProperties.apiKey(), "ai.qwen.api-key"),
                requirePositiveInteger(qwenProperties.maxToolCalls(), "ai.qwen.max-tool-calls"));
    }

    private <T> T requireProperty(T value, String propertyName) {
        if (value == null) {
            throw new IllegalStateException(propertyName + " 必须配置");
        }
        return value;
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " 必须配置");
        }
        return value.trim();
    }

    private int requirePositiveInteger(Integer value, String propertyName) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(propertyName + " 必须配置且大于 0");
        }
        return value;
    }

    /**
     * Qwen 运行时归一化结果。
     * <p>
     * 该类型只暴露已经通过必需项校验的正式值，后续 HTTP 客户端或 Provider 实现不应再重复做空值兜底，
     * 从而保证“启动期 fail-fast，运行期直接消费”的边界清晰。
     *
     * @param baseUrl DashScope OpenAI 兼容模式基础地址
     * @param model Qwen 模型标识
     * @param apiKey DashScope API Key
     * @param maxToolCalls 单轮最大工具调用次数
     */
    public record QwenRuntimeProperties(String baseUrl, String model, String apiKey, int maxToolCalls) {
    }
}
