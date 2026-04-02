package com.jhun.backend.service.support.ai.qwen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Qwen OpenAI 兼容 Chat Completions 客户端。
 * <p>
 * 该客户端专门适配北京地域 DashScope OpenAI 兼容模式下的 `/chat/completions` 协议，
 * 只负责三件事：
 * 1) 基于 `AiRuntimeProperties` 构建稳定的 URL、鉴权头和超时配置；
 * 2) 用最小 DTO 发送普通对话、JSON Mode、`enable_thinking` 与 tools 请求；
 * 3) 把上游错误、畸形 JSON 与空 choices 收口成受控异常，避免脏异常向上层扩散。
 */
public class QwenOpenAiClient {

    private static final String CHAT_COMPLETIONS_URI = "/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public QwenOpenAiClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            AiRuntimeProperties.QwenRuntimeProperties qwenRuntimeProperties) {
        this.restClient = Objects.requireNonNull(restClient, "Qwen RestClient 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        Objects.requireNonNull(qwenRuntimeProperties, "Qwen 运行时配置不能为空");
        this.model = qwenRuntimeProperties.model();
    }

    /**
     * 基于统一运行时配置装配 RestClient Builder。
     * <p>
     * 当前把 base URL、Bearer 鉴权头与请求超时集中放在同一入口，避免后续 Provider 或测试在多个位置重复拼接，
     * 从而保证 `AiRuntimeProperties` 仍是 Qwen 连接信息的唯一真相源。
     *
     * @param restClientBuilder Spring RestClient 构建器
     * @param aiRuntimeProperties 已归一化的 AI 运行时配置
     * @return 已配置 DashScope 兼容模式地址、鉴权头和超时的 Builder
     */
    public static RestClient.Builder configureRestClientBuilder(
            RestClient.Builder restClientBuilder,
            AiRuntimeProperties aiRuntimeProperties) {
        Objects.requireNonNull(restClientBuilder, "RestClient.Builder 不能为空");
        Objects.requireNonNull(aiRuntimeProperties, "AI 运行时配置不能为空");
        AiRuntimeProperties.QwenRuntimeProperties qwenRuntimeProperties = aiRuntimeProperties.qwen();
        if (qwenRuntimeProperties == null) {
            throw new IllegalArgumentException("当前运行时未提供 qwen 配置，无法构建 Qwen OpenAI 客户端");
        }

        return restClientBuilder
                .requestFactory(createRequestFactory(aiRuntimeProperties.requestTimeout()))
                .baseUrl(qwenRuntimeProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + qwenRuntimeProperties.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 调用 Qwen Chat Completions 接口。
     * <p>
     * 当前成功响应只接受至少包含一条 choice 的标准 JSON；若上游返回限流、鉴权失败、服务端错误、空 choices
     * 或无法解析的 JSON，都统一转换为 `QwenOpenAiClientException`，让更上层可以稳定决定是重试、降级还是写失败历史。
     *
     * @param request 最小 Chat Completions 请求 DTO
     * @return 已解析的成功响应 DTO
     */
    public QwenOpenAiChatCompletionResponse createChatCompletion(QwenOpenAiChatCompletionRequest request) {
        Objects.requireNonNull(request, "Qwen 请求不能为空");

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatCompletionPayload(
                            model,
                            request.messages(),
                            request.responseFormat(),
                            request.tools(),
                            request.parallelToolCalls(),
                            request.enableThinking()))
                    .exchange((httpRequest, httpResponse) -> {
                        String body = readBody(httpResponse);
                        throwIfErrorStatus(httpResponse.getStatusCode(), body);
                        return body;
                    });
        } catch (QwenOpenAiClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.TRANSPORT_ERROR,
                    "Qwen 请求超时或网络不可用，请稍后重试");
        } catch (RestClientException exception) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.TRANSPORT_ERROR,
                    "Qwen 请求发送失败，请稍后重试");
        }

        return parseSuccessBody(responseBody);
    }

    private static ClientHttpRequestFactory createRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return requestFactory;
    }

    private void throwIfErrorStatus(HttpStatusCode statusCode, String responseBody) {
        if (statusCode.is2xxSuccessful()) {
            return;
        }

        String upstreamMessage = extractUpstreamMessage(responseBody);
        int rawStatusCode = statusCode.value();
        if (rawStatusCode == 401) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.AUTHENTICATION,
                    mergeMessage("Qwen 鉴权失败，请检查 DASHSCOPE_API_KEY 配置", upstreamMessage),
                    rawStatusCode,
                    upstreamMessage);
        }
        if (rawStatusCode == 429) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.RATE_LIMITED,
                    mergeMessage("Qwen 调用触发限流，请稍后重试", upstreamMessage),
                    rawStatusCode,
                    upstreamMessage);
        }
        if (statusCode.is5xxServerError()) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.UPSTREAM_SERVER_ERROR,
                    mergeMessage("Qwen 服务暂时不可用，请稍后重试", upstreamMessage),
                    rawStatusCode,
                    upstreamMessage);
        }

        throw new QwenOpenAiClientException(
                QwenOpenAiClientException.ErrorType.UPSTREAM_REJECTED,
                mergeMessage("Qwen 请求被上游拒绝", upstreamMessage),
                rawStatusCode,
                upstreamMessage);
    }

    private String extractUpstreamMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            QwenOpenAiErrorResponse errorResponse = objectMapper.readValue(responseBody, QwenOpenAiErrorResponse.class);
            if (errorResponse.error() == null || errorResponse.error().message() == null || errorResponse.error().message().isBlank()) {
                return null;
            }
            return errorResponse.error().message().trim();
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private QwenOpenAiChatCompletionResponse parseSuccessBody(String responseBody) {
        try {
            QwenOpenAiChatCompletionResponse response = objectMapper.readValue(
                    responseBody,
                    QwenOpenAiChatCompletionResponse.class);
            if (response.choices().isEmpty()) {
                throw new QwenOpenAiClientException(
                        QwenOpenAiClientException.ErrorType.INVALID_RESPONSE,
                        "Qwen 返回空 choices，无法生成对话结果");
            }
            return response;
        } catch (QwenOpenAiClientException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.INVALID_RESPONSE,
                    "Qwen 返回了无法解析的 JSON 响应");
        }
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new QwenOpenAiClientException(
                    QwenOpenAiClientException.ErrorType.TRANSPORT_ERROR,
                    "Qwen 响应体读取失败，请稍后重试");
        }
    }

    private String mergeMessage(String baseMessage, String upstreamMessage) {
        if (upstreamMessage == null || upstreamMessage.isBlank()) {
            return baseMessage;
        }
        return baseMessage + "：" + upstreamMessage;
    }

    /**
     * 实际发往上游的最小成功请求体。
     * <p>
     * `model` 由客户端根据运行时配置强制注入，避免调用方在多个地方各自拼模型名，导致请求和配置真相源漂移。
     */
    private record ChatCompletionPayload(
            String model,
            java.util.List<QwenOpenAiChatCompletionRequest.Message> messages,
            @com.fasterxml.jackson.annotation.JsonProperty("response_format")
            QwenOpenAiChatCompletionRequest.ResponseFormat responseFormat,
            java.util.List<QwenOpenAiChatCompletionRequest.Tool> tools,
            @com.fasterxml.jackson.annotation.JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
            @com.fasterxml.jackson.annotation.JsonProperty("enable_thinking") Boolean enableThinking) {
    }
}
