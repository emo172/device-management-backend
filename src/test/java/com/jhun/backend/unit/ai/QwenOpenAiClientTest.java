package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import java.util.LinkedHashMap;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionRequest;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionResponse;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClient;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClientException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Qwen OpenAI 兼容客户端协议级测试。
 * <p>
 * 本测试只锁定顶层任务 3 要求的 HTTP 契约：
 * 正常 completions、JSON Mode、tools 请求、401、429、5xx、空 choices 与畸形 JSON。
 * 这样后续 Provider 接入时可以直接复用客户端，而不必重新猜测 URL、头部或错误映射语义。
 */
class QwenOpenAiClientTest {

    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String MODEL = "qwen-plus";
    private static final String API_KEY = "test-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;

    @AfterEach
    void verifyServer() {
        if (server != null) {
            server.verify();
        }
    }

    /**
     * 验证普通对话请求会命中正式 URL，附带 Bearer 鉴权头与运行时模型名，并能解析文本回答与 usage。
     */
    @Test
    void shouldSendStandardChatCompletionRequest() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("帮我查询今天的预约"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-normal",
                          "model": "qwen-plus",
                          "choices": [
                            {
                              "index": 0,
                              "finish_reason": "stop",
                              "message": {
                                "role": "assistant",
                                "content": "今天你有 2 条预约记录"
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 12,
                            "completion_tokens": 8,
                            "total_tokens": 20
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        QwenOpenAiChatCompletionResponse response = client.createChatCompletion(new QwenOpenAiChatCompletionRequest(
                List.of(
                        new QwenOpenAiChatCompletionRequest.Message("system", "你是预约助手", null),
                        new QwenOpenAiChatCompletionRequest.Message("user", "帮我查询今天的预约", null)),
                null,
                null,
                null));

        assertThat(response.model()).isEqualTo(MODEL);
        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().getFirst().message().content()).isEqualTo("今天你有 2 条预约记录");
        assertThat(response.usage().totalTokens()).isEqualTo(20);
    }

    /**
     * 验证结构化提取所需的 JSON Mode 请求会同时显式发送 `response_format.type=json_object`
     * 与 `enable_thinking=false`，从而锁定计划要求的“JSON Mode + 非思考模式”组合。
     */
    @Test
    void shouldSendJsonModeRequestWithExplicitNonThinkingMode() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.enable_thinking").value(false))
                .andExpect(jsonPath("$.messages[0].content").value("请以 JSON 输出当前意图，JSON 中必须包含 intent 字段"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-json",
                          "model": "qwen-plus",
                          "choices": [
                            {
                              "index": 0,
                              "finish_reason": "stop",
                              "message": {
                                "role": "assistant",
                                "content": "{\\\"intent\\\":\\\"QUERY\\\"}"
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 18,
                            "completion_tokens": 6,
                            "total_tokens": 24
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        QwenOpenAiChatCompletionResponse response = client.createChatCompletion(new QwenOpenAiChatCompletionRequest(
                List.of(new QwenOpenAiChatCompletionRequest.Message(
                        "user",
                        "请以 JSON 输出当前意图，JSON 中必须包含 intent 字段",
                        null)),
                new QwenOpenAiChatCompletionRequest.ResponseFormat("json_object"),
                null,
                null,
                false));

        assertThat(response.choices().getFirst().message().content()).isEqualTo("{\"intent\":\"QUERY\"}");
        assertThat(response.usage().promptTokens()).isEqualTo(18);
    }

    /**
     * 验证 tools 请求会带上函数 schema 与 `parallel_tool_calls=false`，并能稳定解析 tool_calls 结构。
     */
    @Test
    void shouldSendToolsRequestAndParseToolCalls() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("query_my_reservations"))
                .andExpect(jsonPath("$.tools[0].function.parameters.type").value("object"))
                .andExpect(jsonPath("$.parallel_tool_calls").value(false))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-tools",
                          "model": "qwen-plus",
                          "choices": [
                            {
                              "index": 0,
                              "finish_reason": "tool_calls",
                              "message": {
                                "role": "assistant",
                                "tool_calls": [
                                  {
                                    "id": "call_1",
                                    "type": "function",
                                    "function": {
                                      "name": "query_my_reservations",
                                      "arguments": "{\\\"reservationId\\\":\\\"res-1\\\"}"
                                    }
                                  }
                                ]
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 30,
                            "completion_tokens": 10,
                            "total_tokens": 40
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        QwenOpenAiChatCompletionResponse response = client.createChatCompletion(new QwenOpenAiChatCompletionRequest(
                List.of(new QwenOpenAiChatCompletionRequest.Message("user", "请帮我查询预约 res-1", null)),
                null,
                List.of(new QwenOpenAiChatCompletionRequest.Tool(
                        "function",
                        new QwenOpenAiChatCompletionRequest.FunctionDefinition(
                                "query_my_reservations",
                                "查询当前用户的预约详情",
                                reservationIdSchema()))),
                false));

        assertThat(response.choices().getFirst().finishReason()).isEqualTo("tool_calls");
        assertThat(response.choices().getFirst().message().toolCalls()).hasSize(1);
        assertThat(response.choices().getFirst().message().toolCalls().getFirst().function().name())
                .isEqualTo("query_my_reservations");
        assertThat(response.choices().getFirst().message().toolCalls().getFirst().function().arguments())
                .isEqualTo("{\"reservationId\":\"res-1\"}");
    }

    /**
     * 验证 401 会被收口为受控鉴权异常，便于上层提示 API Key 或鉴权配置问题。
     */
    @Test
    void shouldMapUnauthorizedToControlledException() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "Invalid API key"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.createChatCompletion(simpleRequest()))
                .isInstanceOfSatisfying(QwenOpenAiClientException.class, exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(QwenOpenAiClientException.ErrorType.AUTHENTICATION);
                    assertThat(exception.getStatusCode()).isEqualTo(401);
                    assertThat(exception.getMessage()).contains("Qwen 鉴权失败");
                    assertThat(exception.getUpstreamMessage()).isEqualTo("Invalid API key");
                });
    }

    /**
     * 验证 429 会被收口为限流异常，避免上层误判为业务失败或 JSON 解析问题。
     */
    @Test
    void shouldMapRateLimitToControlledException() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "Request rate limited"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.createChatCompletion(simpleRequest()))
                .isInstanceOfSatisfying(QwenOpenAiClientException.class, exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(QwenOpenAiClientException.ErrorType.RATE_LIMITED);
                    assertThat(exception.getStatusCode()).isEqualTo(429);
                    assertThat(exception.getMessage()).contains("Qwen 调用触发限流");
                });
    }

    /**
     * 验证 5xx 会被统一收口为上游服务错误，便于更上层执行受控降级或失败留痕。
     */
    @Test
    void shouldMapServerErrorToControlledException() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "Upstream temporarily unavailable"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.createChatCompletion(simpleRequest()))
                .isInstanceOfSatisfying(QwenOpenAiClientException.class, exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(QwenOpenAiClientException.ErrorType.UPSTREAM_SERVER_ERROR);
                    assertThat(exception.getStatusCode()).isEqualTo(500);
                    assertThat(exception.getMessage()).contains("Qwen 服务暂时不可用");
                });
    }

    /**
     * 验证 200 成功响应但 choices 为空时，会被判定为协议级异常而不是业务成功。
     */
    @Test
    void shouldRejectEmptyChoicesResponse() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-empty",
                          "model": "qwen-plus",
                          "choices": [],
                          "usage": {
                            "prompt_tokens": 10,
                            "completion_tokens": 0,
                            "total_tokens": 10
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createChatCompletion(simpleRequest()))
                .isInstanceOfSatisfying(QwenOpenAiClientException.class, exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(QwenOpenAiClientException.ErrorType.INVALID_RESPONSE);
                    assertThat(exception.getMessage()).contains("空 choices");
                });
    }

    /**
     * 验证畸形 JSON 会被明确收口为响应解析异常，避免底层 Jackson 异常直接向上层泄露。
     */
    @Test
    void shouldRejectMalformedJsonResponse() {
        QwenOpenAiClient client = createClient();
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess("{\"choices\": [", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createChatCompletion(simpleRequest()))
                .isInstanceOfSatisfying(QwenOpenAiClientException.class, exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(QwenOpenAiClientException.ErrorType.INVALID_RESPONSE);
                    assertThat(exception.getMessage()).contains("无法解析的 JSON 响应");
                });
    }

    private QwenOpenAiClient createClient() {
        AiRuntimeProperties aiRuntimeProperties = new AiRuntimeProperties(new AiProperties(
                true,
                AiProperties.Provider.QWEN,
                30,
                new AiProperties.Qwen(BASE_URL, MODEL, API_KEY, 1)));

        RestClient.Builder restClientBuilder = RestClient.builder();
        QwenOpenAiClient.configureRestClientBuilder(restClientBuilder, aiRuntimeProperties);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        return new QwenOpenAiClient(restClientBuilder.build(), objectMapper, aiRuntimeProperties.qwen());
    }

    private QwenOpenAiChatCompletionRequest simpleRequest() {
        return new QwenOpenAiChatCompletionRequest(
                List.of(new QwenOpenAiChatCompletionRequest.Message("user", "帮我查询今天的预约", null)),
                null,
                null,
                null);
    }

    private Map<String, Object> reservationIdSchema() {
        Map<String, Object> reservationId = new LinkedHashMap<>();
        reservationId.put("type", "string");
        reservationId.put("description", "预约编号");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reservationId", reservationId);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", properties);
        root.put("required", List.of("reservationId"));
        return root;
    }
}
