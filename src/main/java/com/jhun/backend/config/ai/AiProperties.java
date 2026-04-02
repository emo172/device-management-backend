package com.jhun.backend.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 运行时配置属性。
 * <p>
 * 该配置对象负责把 `application*.yml` 中的 AI 开关、Provider 选择和 Qwen 专属连接参数集中绑定到同一处，
 * 避免服务层、控制器层或后续 Provider 实现各自散落读取配置，造成运行时语义不一致。
 * 当前字段边界如下：
 * - `enabled`：仅控制新的 `/api/ai/chat` 是否允许进入服务层，不影响历史查询与 Prompt 管理；
 * - `provider`：当前只允许 `mock` 与 `qwen` 两种正式值；
 * - `timeoutSeconds`：后续 AI Provider 统一沿用的请求超时基线；
 * - `qwen`：仅在 `provider=qwen` 时参与必需项校验。
 *
 * @param enabled 是否允许新的 AI 对话请求进入服务层
 * @param provider AI Provider 选择，当前仅支持 mock 与 qwen
 * @param timeoutSeconds AI 请求超时秒数
 * @param qwen Qwen Provider 专属配置
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(boolean enabled, Provider provider, long timeoutSeconds, Qwen qwen) {

    /**
     * AI Provider 正式枚举。
     * <p>
     * 当前任务只允许 `mock` 与 `qwen` 两种值，避免把 Provider 选择语义散落成任意字符串，
     * 为后续 Provider 扩展前先把已有运行时入口收敛到可校验的受控范围。
     */
    public enum Provider {
        MOCK,
        QWEN
    }

    /**
     * Qwen Provider 专属配置。
     * <p>
     * 这些字段只在 `ai.provider=qwen` 时才是必需项；当前阶段虽然尚未真正发起 HTTP 请求，
     * 但必须提前把连接信息收口成正式配置入口，确保缺项能在启动期而不是首条真实请求时暴露。
     *
     * @param baseUrl DashScope OpenAI 兼容模式基础地址
     * @param model Qwen 模型标识，例如 `qwen-plus`
     * @param apiKey DashScope API Key，必须通过环境变量或外部配置注入
     * @param maxToolCalls 单轮最多允许的工具调用次数上限
     */
    public record Qwen(String baseUrl, String model, String apiKey, Integer maxToolCalls) {
    }
}
