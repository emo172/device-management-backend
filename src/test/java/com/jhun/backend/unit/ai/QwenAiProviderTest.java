package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.service.support.ai.AiProviderResult;
import com.jhun.backend.service.support.ai.AiToolExecutionResult;
import com.jhun.backend.service.support.ai.AiToolExecutionService;
import com.jhun.backend.service.support.ai.AiToolRegistry;
import com.jhun.backend.service.support.ai.PromptTemplateSupport;
import com.jhun.backend.service.support.ai.QwenAiProvider;
import com.jhun.backend.service.support.ai.QwenStructuredExtractionPrompt;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionRequest;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionResponse;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClient;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClientException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;

/**
 * QwenAiProvider 三阶段工作流测试。
 * <p>
 * 该测试直接锁定 task 6 的正式边界：
 * 1) 第一阶段必须走 JSON Mode 结构化提取；
 * 2) 第二阶段只有在 toolName 明确且 missingFields 为空时，才允许单次 Function Calling；
 * 3) 第三阶段必须基于工具输出和正式 Prompt 生成最终反馈；
 * 4) 多 tool call、非法参数、上游限流 / 5xx 和阶段冲突都必须返回受控结果。
 */
class QwenAiProviderTest {

    private static final String USER_ID = "user-1";
    private static final String ROLE = "USER";
    private static final String MODEL = "qwen-plus";

    private final QwenOpenAiClient qwenOpenAiClient = mock(QwenOpenAiClient.class);
    private final PromptTemplateSupport promptTemplateSupport = mock(PromptTemplateSupport.class);
    private final AiToolExecutionService aiToolExecutionService = mock(AiToolExecutionService.class);
    private final AiToolRegistry aiToolRegistry = new AiToolRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QwenAiProvider qwenAiProvider = new QwenAiProvider(
            qwenOpenAiClient,
            promptTemplateSupport,
            aiToolExecutionService,
            aiToolRegistry,
            objectMapper,
            new AiRuntimeProperties(new AiProperties(
                    true,
                    AiProperties.Provider.QWEN,
                    30,
                    new AiProperties.Qwen(
                            "https://dashscope.aliyuncs.com/compatible-mode/v1",
                            MODEL,
                            "test-key",
                            1))));

    /**
     * 验证完整成功链路会依次执行“JSON Mode + 非思考模式”的结构化提取、单次 Function Calling、工具执行和最终反馈生成，
     * 且 providerName 必须精确写为 `qwen-plus`。
     */
    @Test
    void shouldCompleteThreeStageWorkflowSuccessfully() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我查询预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我查询预约 RES-1"));
        when(promptTemplateSupport.resolveActiveTemplateContent(com.jhun.backend.common.enums.PromptTemplateType.RESULT_FEEDBACK))
                .thenReturn("请基于执行结果生成结果反馈，不要伪造未执行的动作。");
        when(promptTemplateSupport.resolveActiveTemplateContent(com.jhun.backend.common.enums.PromptTemplateType.CONFLICT_RECOMMENDATION))
                .thenReturn("当执行失败时请说明原因。\n");
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "QUERY",
                                "query_my_reservations",
                                Map.of("reservationId", "RES-1"),
                                List.of(),
                                "可以查询预约详情。",
                                null,
                                "RES-1"),
                        stage2ToolCallResponse("call_1", "query_my_reservations", "{\"reservationId\":\"RES-1\"}"),
                        finalFeedbackResponse("已查询到预约 RES-1，当前状态为 APPROVED。"));
        when(aiToolExecutionService.execute(
                        eq(USER_ID),
                        eq(ROLE),
                        eq("query_my_reservations"),
                        eq(Map.of("reservationId", "RES-1")),
                        eq(null),
                        eq("RES-1")))
                .thenReturn(new AiToolExecutionResult(
                        "query_my_reservations",
                        true,
                        null,
                        "已查询本人预约详情",
                        Map.of("reservation", Map.of("id", "RES-1", "deviceId", "dev-1", "status", "APPROVED")),
                        "{\"toolName\":\"query_my_reservations\",\"success\":true,\"message\":\"已查询本人预约详情\"}"));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");

        ArgumentCaptor<QwenOpenAiChatCompletionRequest> requestCaptor =
                ArgumentCaptor.forClass(QwenOpenAiChatCompletionRequest.class);
        verify(qwenOpenAiClient, times(3)).createChatCompletion(requestCaptor.capture());
        verify(aiToolExecutionService).execute(
                USER_ID,
                ROLE,
                "query_my_reservations",
                Map.of("reservationId", "RES-1"),
                null,
                "RES-1");

        List<QwenOpenAiChatCompletionRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).responseFormat()).isNotNull();
        assertThat(requests.get(0).responseFormat().type()).isEqualTo("json_object");
        assertThat(requests.get(0).enableThinking()).isFalse();
        assertThat(requests.get(1).parallelToolCalls()).isFalse();
        assertThat(requests.get(1).enableThinking()).isNull();
        assertThat(requests.get(1).tools()).hasSize(1);
        assertThat(requests.get(1).tools().getFirst().function().name()).isEqualTo("query_my_reservations");
        assertThat(requests.get(2).enableThinking()).isNull();
        assertThat(requests.get(2).messages().getFirst().content()).contains("RESULT_FEEDBACK");
        assertThat(requests.get(2).messages().get(1).content()).contains("query_my_reservations");
        assertThat(result.executeResult()).isEqualTo("SUCCESS");
        assertThat(result.providerName()).isEqualTo(MODEL);
        assertThat(result.deviceId()).isEqualTo("dev-1");
        assertThat(result.reservationId()).isEqualTo("RES-1");
        assertThat(result.aiResponse()).isEqualTo("已查询到预约 RES-1，当前状态为 APPROVED。");
        assertThat(result.extractedInfo()).contains("query_my_reservations").contains("RES-1");
        assertThat(result.errorMessage()).isNull();
    }

    /**
     * 验证第一阶段若已识别缺失字段，会直接返回受控 PENDING，且不会继续发起工具调用或总结阶段。
     */
    @Test
    void shouldReturnPendingWhenExtractionHasMissingFields() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我取消预约"))
                .thenReturn(structuredExtractionPrompt("帮我取消预约"));
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(stage1ExtractionResponse(
                        "CANCEL",
                        "cancel_my_reservation",
                        Map.of(),
                        List.of("reservationId"),
                        "请先补充预约编号。",
                        null,
                        null));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我取消预约");

        verify(qwenOpenAiClient).createChatCompletion(any());
        verifyNoInteractions(aiToolExecutionService);
        assertThat(result.executeResult()).isEqualTo("PENDING");
        assertThat(result.deviceId()).isNull();
        assertThat(result.reservationId()).isNull();
        assertThat(result.aiResponse()).contains("reservationId").contains("请先补充预约编号");
        assertThat(result.errorMessage()).contains("缺少必要信息");
    }

    /**
     * 验证 Function Calling 若返回多个 tool calls，会被明确拒绝，避免同一轮触发多次业务执行。
     */
    @Test
    void shouldRejectMultipleToolCallsAsControlledFailure() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我查询预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我查询预约 RES-1"));
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "QUERY",
                                "query_my_reservations",
                                Map.of("reservationId", "RES-1"),
                                List.of(),
                                "可以查询预约详情。",
                                null,
                                "RES-1"),
                        new QwenOpenAiChatCompletionResponse(
                                "tool-stage",
                                MODEL,
                                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                                                0,
                                                new QwenOpenAiChatCompletionResponse.Message(
                                                        "assistant",
                                                        null,
                                                        List.of(
                                                                toolCall("call_1", "query_my_reservations", "{\"reservationId\":\"RES-1\"}"),
                                                                toolCall("call_2", "cancel_my_reservation", "{\"reservationId\":\"RES-1\"}"))),
                                                "tool_calls")),
                                null));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");

        verifyNoInteractions(aiToolExecutionService);
        assertThat(result.executeResult()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("最多只允许 1 个工具调用");
        assertThat(result.aiResponse()).contains("工具调用失败");
    }

    /**
     * 验证 Function Calling 参数若不是合法 JSON 对象，会在 provider 层受控失败，而不是把脏参数继续下放到正式业务服务。
     */
    @Test
    void shouldRejectInvalidToolArgumentsBeforeExecutingTool() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我取消预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我取消预约 RES-1"));
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "CANCEL",
                                "cancel_my_reservation",
                                Map.of("reservationId", "RES-1"),
                                List.of(),
                                "可以进入取消流程。",
                                null,
                                "RES-1"),
                        stage2ToolCallResponse("call_1", "cancel_my_reservation", "[\"RES-1\"]"));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我取消预约 RES-1");

        verifyNoInteractions(aiToolExecutionService);
        assertThat(result.executeResult()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("工具参数必须是 JSON 对象");
    }

    /**
     * 验证上游限流或 5xx 会被统一收口为受控 FAILED，且不会静默回退到 mock 继续执行。
     */
    @Test
    void shouldReturnControlledFailureWhenQwenRateLimitedOrServerError() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我查询预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我查询预约 RES-1"));
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenThrow(new QwenOpenAiClientException(
                        QwenOpenAiClientException.ErrorType.RATE_LIMITED,
                        "Qwen 调用触发限流，请稍后重试"))
                .thenThrow(new QwenOpenAiClientException(
                        QwenOpenAiClientException.ErrorType.UPSTREAM_SERVER_ERROR,
                        "Qwen 服务暂时不可用，请稍后重试"));

        AiProviderResult firstResult = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");
        AiProviderResult secondResult = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");

        verifyNoInteractions(aiToolExecutionService);
        assertThat(firstResult.executeResult()).isEqualTo("FAILED");
        assertThat(firstResult.errorMessage()).contains("限流");
        assertThat(secondResult.executeResult()).isEqualTo("FAILED");
        assertThat(secondResult.errorMessage()).contains("暂时不可用");
    }

    /**
     * 验证结构化提取阶段与 Function Calling 阶段若给出不同工具名，会被判定为阶段冲突并受控失败。
     */
    @Test
    void shouldRejectStageConflictWhenToolNameDiffersFromExtraction() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我查询预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我查询预约 RES-1"));
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "QUERY",
                                "query_my_reservations",
                                Map.of("reservationId", "RES-1"),
                                List.of(),
                                "可以查询预约详情。",
                                null,
                                "RES-1"),
                        stage2ToolCallResponse("call_1", "cancel_my_reservation", "{\"reservationId\":\"RES-1\"}"));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");

        verifyNoInteractions(aiToolExecutionService);
        assertThat(result.executeResult()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("与结构化提取阶段冲突");
    }

    /**
     * 验证工具执行成功后，如果最终反馈阶段遭遇 5xx，也会返回受控 FAILED，避免把上游异常直接暴露出去。
     */
    @Test
    void shouldReturnControlledFailureWhenFinalFeedbackStageFails() {
        when(promptTemplateSupport.resolveStructuredExtractionPrompt("帮我查询预约 RES-1"))
                .thenReturn(structuredExtractionPrompt("帮我查询预约 RES-1"));
        when(promptTemplateSupport.resolveActiveTemplateContent(com.jhun.backend.common.enums.PromptTemplateType.RESULT_FEEDBACK))
                .thenReturn("请基于执行结果生成结果反馈，不要伪造未执行的动作。");
        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "QUERY",
                                "query_my_reservations",
                                Map.of("reservationId", "RES-1"),
                                List.of(),
                                "可以查询预约详情。",
                                null,
                                "RES-1"),
                        stage2ToolCallResponse("call_1", "query_my_reservations", "{\"reservationId\":\"RES-1\"}"))
                .thenThrow(new QwenOpenAiClientException(
                        QwenOpenAiClientException.ErrorType.UPSTREAM_SERVER_ERROR,
                        "Qwen 服务暂时不可用，请稍后重试"));
        when(aiToolExecutionService.execute(
                        eq(USER_ID),
                        eq(ROLE),
                        eq("query_my_reservations"),
                        eq(Map.of("reservationId", "RES-1")),
                        eq(null),
                        eq("RES-1")))
                .thenReturn(new AiToolExecutionResult(
                        "query_my_reservations",
                        true,
                        null,
                        "已查询本人预约详情",
                        Map.of("reservation", Map.of("id", "RES-1")),
                        "{\"toolName\":\"query_my_reservations\",\"success\":true}"));

        AiProviderResult result = qwenAiProvider.process(USER_ID, ROLE, "帮我查询预约 RES-1");

        verify(aiToolExecutionService).execute(
                USER_ID,
                ROLE,
                "query_my_reservations",
                Map.of("reservationId", "RES-1"),
                null,
                "RES-1");
        assertThat(result.executeResult()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("暂时不可用");
    }

    private QwenOpenAiChatCompletionResponse stage1ExtractionResponse(
            String intent,
            String toolName,
            Map<String, Object> toolArguments,
            List<String> missingFields,
            String replyHint,
            String resolvedDeviceId,
            String resolvedReservationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", intent);
        payload.put("intentConfidence", 0.95);
        payload.put("toolName", toolName);
        payload.put("toolArguments", toolArguments);
        payload.put("missingFields", missingFields);
        payload.put("replyHint", replyHint);
        payload.put("resolvedDeviceId", resolvedDeviceId);
        payload.put("resolvedReservationId", resolvedReservationId);
        try {
            return new QwenOpenAiChatCompletionResponse(
                    "stage-1",
                    MODEL,
                    List.of(new QwenOpenAiChatCompletionResponse.Choice(
                            0,
                            new QwenOpenAiChatCompletionResponse.Message(
                                    "assistant",
                                    objectMapper.writeValueAsString(payload),
                                    List.of()),
                            "stop")),
                    null);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private QwenOpenAiChatCompletionResponse stage2ToolCallResponse(String callId, String toolName, String arguments) {
        return new QwenOpenAiChatCompletionResponse(
                "stage-2",
                MODEL,
                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                        0,
                        new QwenOpenAiChatCompletionResponse.Message(
                                "assistant",
                                null,
                                List.of(toolCall(callId, toolName, arguments))),
                        "tool_calls")),
                null);
    }

    private QwenOpenAiChatCompletionResponse finalFeedbackResponse(String content) {
        return new QwenOpenAiChatCompletionResponse(
                "stage-3",
                MODEL,
                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                        0,
                        new QwenOpenAiChatCompletionResponse.Message("assistant", content, List.of()),
                        "stop")),
                null);
    }

    private QwenOpenAiChatCompletionResponse.ToolCall toolCall(String callId, String toolName, String arguments) {
        return new QwenOpenAiChatCompletionResponse.ToolCall(
                callId,
                "function",
                new QwenOpenAiChatCompletionResponse.FunctionCall(toolName, arguments));
    }

    private QwenStructuredExtractionPrompt structuredExtractionPrompt(String userMessage) {
        return new QwenStructuredExtractionPrompt(
                "intent-template",
                "info-template",
                "{}",
                "请以 JSON 输出结构化提取结果。\n当前用户输入：%s".formatted(userMessage),
                "上游固定采用单轮输入策略，只分析当前这一轮用户输入，不拼接任何历史对话。",
                "sessionId 仅用于本地历史归组，不会作为多轮上下文发送给上游模型。");
    }
}
