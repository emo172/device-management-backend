package com.jhun.backend.service.support.ai.qwen;

/**
 * Qwen OpenAI 兼容客户端受控异常。
 * <p>
 * 当前客户端层不直接抛出散乱的 `RestClientException` 或 Jackson 解析异常，
 * 而是统一折叠成可预测的错误类型与中文消息，方便后续 Provider 或 Service
 * 层按现有 `BusinessException` 风格继续收口，而不泄露底层 HTTP / JSON 细节。
 */
public class QwenOpenAiClientException extends RuntimeException {

    /**
     * 当前失败所属的受控错误类型。
     * <p>
     * 这里故意只保留后续 Provider 真正需要区分的协议级分支，避免把上游细节过度建模成复杂错误体系。
     */
    private final ErrorType errorType;

    /**
     * 上游 HTTP 状态码。
     * <p>
     * 非 HTTP 场景（例如畸形 JSON）允许为空，避免调用方误把所有失败都解释成可重试的网络错误。
     */
    private final Integer statusCode;

    /**
     * 上游可读错误消息。
     * <p>
     * 该字段用于保留 DashScope / OpenAI 兼容接口返回的业务性提示，便于后续日志与失败反馈拼接，
     * 但最终对外是否原样暴露应由更上层决定。
     */
    private final String upstreamMessage;

    public QwenOpenAiClientException(ErrorType errorType, String message) {
        this(errorType, message, null, null);
    }

    public QwenOpenAiClientException(ErrorType errorType, String message, Integer statusCode, String upstreamMessage) {
        super(message);
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.upstreamMessage = upstreamMessage;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getUpstreamMessage() {
        return upstreamMessage;
    }

    /**
     * Qwen 客户端当前支持的最小错误类型集合。
     * <p>
     * 这些类型直接对应本任务要求的 401 / 429 / 5xx / 非法 JSON / 空 choices 等正式分支，
     * 既能满足后续 Provider 的策略判断，也避免把本次任务提前扩展成通用错误平台。
     */
    public enum ErrorType {
        AUTHENTICATION,
        RATE_LIMITED,
        UPSTREAM_SERVER_ERROR,
        UPSTREAM_REJECTED,
        INVALID_RESPONSE,
        TRANSPORT_ERROR
    }
}
