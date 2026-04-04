package com.jhun.backend.service.support.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionRequest;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionResponse;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClient;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClientException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Qwen-Plus Provider 三阶段工作流实现。
 * <p>
 * 该实现严格按 task 6 的固定顺序执行：
 * 1) 用 JSON Mode + 显式非思考模式做单轮意图识别与结构化提取；
 * 2) 只有 `toolName` 明确且 `missingFields` 为空时，才进入单次 Function Calling；
 * 3) 用工具输出和正式 Prompt 生成最终反馈。
 * <p>
 * 这里显式承担三类红线：
 * 1) 不拼接历史消息，始终只处理当前这一轮输入；
 * 2) 不允许并行或多次工具调用，单轮最多执行 1 个白名单工具；
 * 3) 上游失败、阶段冲突、非法参数和工具执行失败都必须收口成受控 `SUCCESS` / `PENDING` / `FAILED` 结果，
 *    严禁静默切回 mock 并继续真实业务写入。
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "qwen")
public class QwenAiProvider implements AiProvider {

    private static final QwenOpenAiChatCompletionRequest.ResponseFormat JSON_OBJECT_RESPONSE_FORMAT =
            new QwenOpenAiChatCompletionRequest.ResponseFormat("json_object");

    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";

    private final QwenOpenAiClient qwenOpenAiClient;
    private final PromptTemplateSupport promptTemplateSupport;
    private final AiToolExecutionService aiToolExecutionService;
    private final AiToolRegistry aiToolRegistry;
    private final ObjectMapper objectMapper;
    private final String providerName;
    private final int maxToolCalls;

    public QwenAiProvider(
            QwenOpenAiClient qwenOpenAiClient,
            PromptTemplateSupport promptTemplateSupport,
            AiToolExecutionService aiToolExecutionService,
            AiToolRegistry aiToolRegistry,
            ObjectMapper objectMapper,
            AiRuntimeProperties aiRuntimeProperties) {
        this.qwenOpenAiClient = Objects.requireNonNull(qwenOpenAiClient, "QwenOpenAiClient 不能为空");
        this.promptTemplateSupport = Objects.requireNonNull(promptTemplateSupport, "PromptTemplateSupport 不能为空");
        this.aiToolExecutionService = Objects.requireNonNull(aiToolExecutionService, "AiToolExecutionService 不能为空");
        this.aiToolRegistry = Objects.requireNonNull(aiToolRegistry, "AiToolRegistry 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        Objects.requireNonNull(aiRuntimeProperties, "AiRuntimeProperties 不能为空");
        if (aiRuntimeProperties.qwen() == null) {
            throw new IllegalStateException("QwenAiProvider 需要显式 qwen 运行时配置");
        }
        this.providerName = aiRuntimeProperties.qwen().model();
        this.maxToolCalls = Math.min(aiRuntimeProperties.qwen().maxToolCalls(), 1);
    }

    @Override
    public AiProperties.Provider provider() {
        return AiProperties.Provider.QWEN;
    }

    /**
     * 执行 Qwen 三阶段工作流。
     * <p>
     * 该方法只消费当前这一轮消息和当前登录用户上下文，不接入会话历史；
     * 真正的业务动作统一由 {@link AiToolExecutionService} 执行，Provider 自身只负责上游编排、阶段校验与结果收口。
     */
    @Override
    public AiProviderResult process(String userId, String role, String message) {
        String normalizedMessage = normalizeMessage(message);
        if (normalizedMessage == null) {
            return failed(AiIntentType.UNKNOWN, null, null, "当前消息为空，无法进行 AI 处理");
        }

        StageOneOutcome stageOneOutcome = runStructuredExtraction(normalizedMessage);
        if (!stageOneOutcome.success()) {
            return failed(AiIntentType.UNKNOWN, null, null, stageOneOutcome.failureReason());
        }

        QwenExtractionSchema.StructuredExtraction extraction = stageOneOutcome.extraction();
        AiIntentType intent = AiIntentType.valueOf(extraction.intent());
        String extractedInfo = stageOneOutcome.extractedInfo();
        ResolvedResourceIds extractionResources = resolveExtractionResources(extraction);

        if (!extraction.missingFields().isEmpty()) {
            return pending(intent, extraction.intentConfidence(), extractedInfo, buildPendingMessage(extraction), extractionResources);
        }

        if (extraction.toolName() == null) {
            return handleNoExplicitTool(normalizedMessage, intent, extraction, extractedInfo, extractionResources);
        }

        StageTwoOutcome stageTwoOutcome = runSingleToolFunctionCalling(normalizedMessage, extraction, extractedInfo);
        if (!stageTwoOutcome.success()) {
            return failed(intent, extraction.intentConfidence(), extractedInfo, stageTwoOutcome.failureReason(), extractionResources);
        }

        AiToolExecutionResult toolExecutionResult = aiToolExecutionService.execute(
                userId,
                role,
                stageTwoOutcome.toolName(),
                stageTwoOutcome.toolArguments(),
                extraction.resolvedDeviceId(),
                extraction.resolvedReservationId());
        ResolvedResourceIds resolvedResources = mergeResolvedResourceIds(resolveToolResources(toolExecutionResult), extractionResources);

        if (!toolExecutionResult.success()) {
            AiExecuteResult executeResult = classifyToolFailure(toolExecutionResult.errorCode());
            FinalFeedbackOutcome feedbackOutcome = generateFinalFeedback(
                    normalizedMessage,
                    extractedInfo,
                    toolExecutionResult,
                    executeResult,
                    true);
            if (!feedbackOutcome.success()) {
                if (AiExecuteResult.PENDING.equals(executeResult)) {
                    return pending(
                            intent,
                            extraction.intentConfidence(),
                            extractedInfo,
                            buildPendingMessage(extraction, toolExecutionResult.message()),
                            resolvedResources);
                }
                return failed(intent, extraction.intentConfidence(), extractedInfo, feedbackOutcome.failureReason(), resolvedResources);
            }
            if (AiExecuteResult.PENDING.equals(executeResult)) {
                return pending(intent, extraction.intentConfidence(), extractedInfo, feedbackOutcome.content(), resolvedResources);
            }
            return failed(
                    intent,
                    extraction.intentConfidence(),
                    extractedInfo,
                    feedbackOutcome.content(),
                    toolExecutionResult.message(),
                    resolvedResources);
        }

        FinalFeedbackOutcome feedbackOutcome = generateFinalFeedback(
                normalizedMessage,
                extractedInfo,
                toolExecutionResult,
                AiExecuteResult.SUCCESS,
                false);
        if (!feedbackOutcome.success()) {
            return failed(
                    intent,
                    extraction.intentConfidence(),
                    extractedInfo,
                    "AI 工具已执行，但生成最终反馈失败，请以正式业务记录为准。" + feedbackOutcome.failureReason(),
                    feedbackOutcome.failureReason(),
                    resolvedResources);
        }
        return success(intent, extraction.intentConfidence(), extractedInfo, feedbackOutcome.content(), resolvedResources);
    }

    private StageOneOutcome runStructuredExtraction(String message) {
        try {
            QwenStructuredExtractionPrompt prompt = promptTemplateSupport.resolveStructuredExtractionPrompt(message);
            QwenOpenAiChatCompletionRequest request = new QwenOpenAiChatCompletionRequest(
                    List.of(new QwenOpenAiChatCompletionRequest.Message(USER_ROLE, prompt.upstreamPrompt(), null)),
                    JSON_OBJECT_RESPONSE_FORMAT,
                    null,
                    null,
                    false);
            QwenOpenAiChatCompletionResponse response = qwenOpenAiClient.createChatCompletion(request);
            String rawJson = assistantContent(response);
            QwenExtractionSchema.ParseResult parseResult = QwenExtractionSchema.parse(rawJson);
            if (!parseResult.success()) {
                return StageOneOutcome.failed(parseResult.failureReason());
            }
            return StageOneOutcome.succeeded(parseResult.payload(), serializeExtraction(parseResult.payload()));
        } catch (BusinessException exception) {
            /*
             * Prompt 模板脏数据属于运维配置问题，不是 Qwen 上游瞬时故障。
             * 这里必须原样抛出，才能继续保持现有“发现多条启用模板就 fail-fast 返回 400”的控制器契约。
             */
            throw exception;
        } catch (QwenOpenAiClientException exception) {
            return StageOneOutcome.failed(exception.getMessage());
        } catch (RuntimeException exception) {
            return StageOneOutcome.failed("结构化提取阶段失败，请稍后重试");
        }
    }

    private AiProviderResult handleNoExplicitTool(
            String message,
            AiIntentType intent,
            QwenExtractionSchema.StructuredExtraction extraction,
            String extractedInfo,
            ResolvedResourceIds resolvedResourceIds) {
        if (AiIntentType.HELP.equals(intent)) {
            FinalFeedbackOutcome feedbackOutcome = generateFinalFeedback(
                    message,
                    extractedInfo,
                    null,
                    AiExecuteResult.SUCCESS,
                    false);
            if (!feedbackOutcome.success()) {
                return failed(intent, extraction.intentConfidence(), extractedInfo, feedbackOutcome.failureReason(), resolvedResourceIds);
            }
            return success(intent, extraction.intentConfidence(), extractedInfo, feedbackOutcome.content(), resolvedResourceIds);
        }
        if (AiIntentType.UNKNOWN.equals(intent)) {
            return failed(intent, extraction.intentConfidence(), extractedInfo, firstNonBlank(
                    extraction.replyHint(),
                    "当前未识别到明确的 AI 意图，请补充更具体的预约、查询或取消信息。"), resolvedResourceIds);
        }
        return pending(intent, extraction.intentConfidence(), extractedInfo, buildPendingMessage(extraction,
                firstNonBlank(extraction.replyHint(), "当前尚未定位到唯一可执行工具，请补充更明确的信息。")), resolvedResourceIds);
    }

    private StageTwoOutcome runSingleToolFunctionCalling(
            String message,
            QwenExtractionSchema.StructuredExtraction extraction,
            String extractedInfo) {
        AiToolRegistry.ToolDefinition toolDefinition = aiToolRegistry.definitionOf(extraction.toolName());
        if (toolDefinition == null) {
            return StageTwoOutcome.failed("结构化提取阶段给出了未注册工具，当前无法继续执行");
        }
        try {
            QwenOpenAiChatCompletionRequest request = new QwenOpenAiChatCompletionRequest(
                    List.of(
                            new QwenOpenAiChatCompletionRequest.Message(
                                    SYSTEM_ROLE,
                                    "你正在决定是否执行唯一允许的工具调用。单轮最多只能返回 1 个 tool_call，工具名必须与结构化提取阶段一致。",
                                    null),
                            new QwenOpenAiChatCompletionRequest.Message(
                                    USER_ROLE,
                                    buildFunctionCallingPrompt(message, extractedInfo, extraction.toolName()),
                                    null)),
                    null,
                    List.of(new QwenOpenAiChatCompletionRequest.Tool(
                            "function",
                            new QwenOpenAiChatCompletionRequest.FunctionDefinition(
                                    toolDefinition.name(),
                                    toolDefinition.description(),
                                    toolDefinition.parameters()))),
                    false);
            QwenOpenAiChatCompletionResponse response = qwenOpenAiClient.createChatCompletion(request);
            List<QwenOpenAiChatCompletionResponse.ToolCall> toolCalls = firstChoiceToolCalls(response);
            if (toolCalls.isEmpty()) {
                return StageTwoOutcome.failed("Function Calling 阶段未返回工具调用，与结构化提取阶段冲突");
            }
            if (toolCalls.size() > maxToolCalls) {
                return StageTwoOutcome.failed("当前最多只允许 1 个工具调用，已拒绝继续执行");
            }

            QwenOpenAiChatCompletionResponse.ToolCall toolCall = toolCalls.getFirst();
            if (toolCall.function() == null || toolCall.function().name() == null) {
                return StageTwoOutcome.failed("Function Calling 阶段缺少合法函数名称");
            }
            if (!extraction.toolName().equals(toolCall.function().name().trim())) {
                return StageTwoOutcome.failed("Function Calling 阶段返回的工具名与结构化提取阶段冲突");
            }

            Map<String, Object> toolArguments = parseToolArguments(toolCall.function().arguments());
            return StageTwoOutcome.succeeded(toolCall.function().name().trim(), toolArguments);
        } catch (QwenOpenAiClientException exception) {
            return StageTwoOutcome.failed(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return StageTwoOutcome.failed(exception.getMessage());
        } catch (RuntimeException exception) {
            return StageTwoOutcome.failed("Function Calling 阶段失败，请稍后重试");
        }
    }

    private FinalFeedbackOutcome generateFinalFeedback(
            String message,
            String extractedInfo,
            AiToolExecutionResult toolExecutionResult,
            AiExecuteResult executeResult,
            boolean useConflictPrompt) {
        PromptTemplateType promptType = useConflictPrompt
                ? PromptTemplateType.CONFLICT_RECOMMENDATION
                : PromptTemplateType.RESULT_FEEDBACK;
        try {
            String templateContent = promptTemplateSupport.resolveActiveTemplateContent(promptType);
            String toolResultJson = toolExecutionResult == null ? "{}" : toolExecutionResult.serializedResult();
            QwenOpenAiChatCompletionRequest request = new QwenOpenAiChatCompletionRequest(
                    List.of(
                            new QwenOpenAiChatCompletionRequest.Message(
                                    SYSTEM_ROLE,
                                    "【%s 模板】\n%s".formatted(promptType.name(), templateContent),
                                    null),
                            new QwenOpenAiChatCompletionRequest.Message(
                                    USER_ROLE,
                                    buildFinalFeedbackPrompt(message, extractedInfo, toolResultJson, executeResult),
                                    null)),
                    null,
                    null,
                    null);
            QwenOpenAiChatCompletionResponse response = qwenOpenAiClient.createChatCompletion(request);
            String content = assistantContent(response);
            if (content == null || content.isBlank()) {
                return FinalFeedbackOutcome.failed("最终反馈阶段返回空内容，无法生成对用户可读的结果");
            }
            return FinalFeedbackOutcome.succeeded(content.trim());
        } catch (BusinessException exception) {
            /*
             * RESULT_FEEDBACK / CONFLICT_RECOMMENDATION 模板冲突同样属于必须立即暴露的脏配置。
             * 如果在这里吞成通用 FAILED，运维侧就无法第一时间清理模板真相源，用户也会拿到误导性的 AI 错误信息。
             */
            throw exception;
        } catch (QwenOpenAiClientException exception) {
            return FinalFeedbackOutcome.failed(exception.getMessage());
        } catch (RuntimeException exception) {
            return FinalFeedbackOutcome.failed("最终反馈阶段失败，请稍后重试");
        }
    }

    private Map<String, Object> parseToolArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            throw new IllegalArgumentException("Function Calling 阶段缺少工具参数，当前不能继续执行");
        }
        try {
            JsonNode root = objectMapper.readTree(rawArguments);
            if (!root.isObject()) {
                throw new IllegalArgumentException("工具参数必须是 JSON 对象");
            }
            return objectMapper.convertValue(root, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工具参数不是合法 JSON，当前不能继续执行");
        }
    }

    private List<QwenOpenAiChatCompletionResponse.ToolCall> firstChoiceToolCalls(QwenOpenAiChatCompletionResponse response) {
        QwenOpenAiChatCompletionResponse.Choice choice = firstChoice(response);
        if (choice.message() == null || choice.message().toolCalls() == null) {
            return List.of();
        }
        return choice.message().toolCalls();
    }

    private String assistantContent(QwenOpenAiChatCompletionResponse response) {
        QwenOpenAiChatCompletionResponse.Choice choice = firstChoice(response);
        if (choice.message() == null) {
            throw new IllegalArgumentException("Qwen 返回缺少 message 节点，当前无法继续处理");
        }
        return choice.message().content();
    }

    private QwenOpenAiChatCompletionResponse.Choice firstChoice(QwenOpenAiChatCompletionResponse response) {
        if (response == null || response.choices().isEmpty()) {
            throw new IllegalArgumentException("Qwen 返回空 choices，当前无法继续处理");
        }
        return response.choices().getFirst();
    }

    private String buildFunctionCallingPrompt(String message, String extractedInfo, String expectedToolName) {
        return """
                你正在根据单轮结构化提取结果决定是否执行工具调用。
                当前只允许调用一个工具，且工具名必须为：%s。
                如果无法满足这一条件，不要生成多个 tool_call。

                【当前用户输入】
                %s

                【结构化提取结果】
                %s
                """.formatted(expectedToolName, message, extractedInfo);
    }

    private String buildFinalFeedbackPrompt(
            String message,
            String extractedInfo,
            String toolResultJson,
            AiExecuteResult executeResult) {
        return """
                请基于当前这一轮输入生成最终反馈，不要拼接历史对话，也不要伪造未执行的业务动作。

                【当前用户输入】
                %s

                【结构化提取结果】
                %s

                【工具执行结果】
                %s

                【当前判定执行状态】
                %s
                """.formatted(message, extractedInfo, toolResultJson, executeResult.name());
    }

    private String serializeExtraction(QwenExtractionSchema.StructuredExtraction extraction) {
        try {
            return objectMapper.writeValueAsString(extraction);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private ResolvedResourceIds resolveExtractionResources(QwenExtractionSchema.StructuredExtraction extraction) {
        return new ResolvedResourceIds(
                firstNonBlank(extraction.resolvedDeviceId(), readExplicitIdArgument(extraction.toolArguments(), "deviceId")),
                firstNonBlank(extraction.resolvedReservationId(), readExplicitIdArgument(extraction.toolArguments(), "reservationId")));
    }

    private ResolvedResourceIds resolveToolResources(AiToolExecutionResult toolExecutionResult) {
        if (toolExecutionResult == null) {
            return ResolvedResourceIds.empty();
        }
        Map<String, Object> payload = toolExecutionResult.payload();
        return new ResolvedResourceIds(
                firstNonBlank(
                        readNestedId(payload.get("device")),
                        readNestedText(payload.get("reservation"), "deviceId"),
                        readTopLevelText(payload, "deviceId")),
                firstNonBlank(
                        readNestedId(payload.get("reservation")),
                        readTopLevelText(payload, "reservationId")));
    }

    private ResolvedResourceIds mergeResolvedResourceIds(ResolvedResourceIds primary, ResolvedResourceIds fallback) {
        return new ResolvedResourceIds(
                firstNonBlank(primary.deviceId(), fallback.deviceId()),
                firstNonBlank(primary.reservationId(), fallback.reservationId()));
    }

    private String readExplicitIdArgument(Map<String, Object> toolArguments, String fieldName) {
        Object rawValue = toolArguments.get(fieldName);
        if (!(rawValue instanceof String textValue)) {
            return null;
        }
        return normalizeMessage(textValue);
    }

    private String readNestedId(Object rawNode) {
        return readNestedText(rawNode, "id");
    }

    private String readNestedText(Object rawNode, String fieldName) {
        if (!(rawNode instanceof Map<?, ?> nestedMap)) {
            return null;
        }
        Object rawValue = nestedMap.get(fieldName);
        if (!(rawValue instanceof String textValue)) {
            return null;
        }
        return normalizeMessage(textValue);
    }

    private String readTopLevelText(Map<String, Object> payload, String fieldName) {
        Object rawValue = payload.get(fieldName);
        if (!(rawValue instanceof String textValue)) {
            return null;
        }
        return normalizeMessage(textValue);
    }

    private AiExecuteResult classifyToolFailure(String errorCode) {
        if ("AMBIGUOUS_RESOURCE".equals(errorCode) || "RESOURCE_NOT_FOUND".equals(errorCode)) {
            return AiExecuteResult.PENDING;
        }
        return AiExecuteResult.FAILED;
    }

    private String buildPendingMessage(QwenExtractionSchema.StructuredExtraction extraction) {
        String fieldsMessage = extraction.missingFields().isEmpty()
                ? null
                : "缺少必要信息：" + String.join("、", extraction.missingFields());
        return buildPendingMessage(extraction, fieldsMessage);
    }

    private String buildPendingMessage(QwenExtractionSchema.StructuredExtraction extraction, String fallbackReason) {
        return firstNonBlank(
                joinMessages(extraction.replyHint(), fallbackReason),
                "当前仍缺少继续执行所需的信息，请补充后重试。");
    }

    private String joinMessages(String first, String second) {
        String normalizedFirst = normalizeMessage(first);
        String normalizedSecond = normalizeMessage(second);
        if (normalizedFirst == null) {
            return normalizedSecond;
        }
        if (normalizedSecond == null) {
            return normalizedFirst;
        }
        return normalizedFirst + " " + normalizedSecond;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeMessage(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AiProviderResult success(
            AiIntentType intent,
            BigDecimal confidence,
            String extractedInfo,
            String aiResponse,
            ResolvedResourceIds resolvedResourceIds) {
        return new AiProviderResult(
                intent.name(),
                confidence,
                extractedInfo,
                AiExecuteResult.SUCCESS.name(),
                aiResponse,
                null,
                providerName,
                resolvedResourceIds.deviceId(),
                resolvedResourceIds.reservationId());
    }

    private AiProviderResult pending(
            AiIntentType intent,
            BigDecimal confidence,
            String extractedInfo,
            String aiResponse,
            ResolvedResourceIds resolvedResourceIds) {
        String errorMessage = aiResponse == null ? "当前处于待补充信息状态" : aiResponse;
        return new AiProviderResult(
                intent.name(),
                confidence,
                extractedInfo,
                AiExecuteResult.PENDING.name(),
                aiResponse,
                errorMessage,
                providerName,
                resolvedResourceIds.deviceId(),
                resolvedResourceIds.reservationId());
    }

    private AiProviderResult failed(
            AiIntentType intent,
            BigDecimal confidence,
            String extractedInfo,
            String reason) {
        return failed(intent, confidence, extractedInfo, "AI 工具调用失败：" + reason, reason, ResolvedResourceIds.empty());
    }

    private AiProviderResult failed(
            AiIntentType intent,
            BigDecimal confidence,
            String extractedInfo,
            String reason,
            ResolvedResourceIds resolvedResourceIds) {
        return failed(intent, confidence, extractedInfo, "AI 工具调用失败：" + reason, reason, resolvedResourceIds);
    }

    private AiProviderResult failed(
            AiIntentType intent,
            BigDecimal confidence,
            String extractedInfo,
            String aiResponse,
            String errorMessage,
            ResolvedResourceIds resolvedResourceIds) {
        return new AiProviderResult(
                intent.name(),
                confidence,
                extractedInfo,
                AiExecuteResult.FAILED.name(),
                aiResponse,
                errorMessage,
                providerName,
                resolvedResourceIds.deviceId(),
                resolvedResourceIds.reservationId());
    }

    private record StageOneOutcome(boolean success, QwenExtractionSchema.StructuredExtraction extraction, String extractedInfo,
                                   String failureReason) {

        private static StageOneOutcome succeeded(QwenExtractionSchema.StructuredExtraction extraction, String extractedInfo) {
            return new StageOneOutcome(true, extraction, extractedInfo, null);
        }

        private static StageOneOutcome failed(String failureReason) {
            return new StageOneOutcome(false, null, null, failureReason);
        }
    }

    private record StageTwoOutcome(boolean success, String toolName, Map<String, Object> toolArguments, String failureReason) {

        private static StageTwoOutcome succeeded(String toolName, Map<String, Object> toolArguments) {
            return new StageTwoOutcome(true, toolName, new LinkedHashMap<>(toolArguments), null);
        }

        private static StageTwoOutcome failed(String failureReason) {
            return new StageTwoOutcome(false, null, Map.of(), failureReason);
        }
    }

    private record FinalFeedbackOutcome(boolean success, String content, String failureReason) {

        private static FinalFeedbackOutcome succeeded(String content) {
            return new FinalFeedbackOutcome(true, content, null);
        }

        private static FinalFeedbackOutcome failed(String failureReason) {
            return new FinalFeedbackOutcome(false, null, failureReason);
        }
    }

    private record ResolvedResourceIds(String deviceId, String reservationId) {

        private static ResolvedResourceIds empty() {
            return new ResolvedResourceIds(null, null);
        }
    }
}
