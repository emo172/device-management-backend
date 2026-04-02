package com.jhun.backend.service.support.ai.qwen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Qwen OpenAI 兼容错误响应 DTO。
 * <p>
 * DashScope 兼容模式沿用 OpenAI 风格的 `error` 包装对象；当前只保留错误消息、类型与代码三个字段，
 * 因为本任务只需要把 401 / 429 / 5xx 等上游错误转成受控异常，不需要在客户端层完整复制上游所有元数据。
 *
 * @param error 上游错误对象，为空时说明上游没有返回标准错误体，只能退化为状态码级失败
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QwenOpenAiErrorResponse(ErrorDetail error) {

    /**
     * 上游错误详情。
     *
     * @param message 上游可读错误消息
     * @param type 上游错误类型
     * @param code 上游错误码
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(String message, String type, String code) {
    }
}
