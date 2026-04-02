package com.jhun.backend.service.support.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen 结构化提取 Schema 与解析辅助。
 * <p>
 * 该类不是 provider 实现本身，而是 task 4 输出的结构化真相源：
 * 1) 固定后续 JSON Mode 需要遵守的字段集合；
 * 2) 提供“Prompt 必须包含 JSON 关键词”的受控校验；
 * 3) 提供对畸形 JSON / 缺字段响应的稳定解析结果，避免后续 provider 直接把底层解析异常泄露到业务层。
 */
public final class QwenExtractionSchema {

    /** JSON Mode 对 Prompt 的显式关键词要求。 */
    public static final String JSON_KEYWORD = "JSON";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> REQUIRED_FIELDS = List.of(
            "intent",
            "intentConfidence",
            "toolName",
            "toolArguments",
            "missingFields",
            "replyHint",
            "resolvedDeviceId",
            "resolvedReservationId");

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "required": [
                "intent",
                "intentConfidence",
                "toolName",
                "toolArguments",
                "missingFields",
                "replyHint",
                "resolvedDeviceId",
                "resolvedReservationId"
              ],
              "properties": {
                "intent": {
                  "type": "string",
                  "enum": ["RESERVE", "QUERY", "CANCEL", "HELP", "UNKNOWN"]
                },
                "intentConfidence": {
                  "type": "number",
                  "minimum": 0,
                  "maximum": 1
                },
                "toolName": {
                  "type": ["string", "null"]
                },
                "toolArguments": {
                  "type": "object"
                },
                "missingFields": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "replyHint": {
                  "type": ["string", "null"]
                },
                "resolvedDeviceId": {
                  "type": ["string", "null"]
                },
                "resolvedReservationId": {
                  "type": ["string", "null"]
                }
              }
            }
            """;

    private QwenExtractionSchema() {
    }

    /**
     * 返回统一结构化提取 schema。
     *
     * @return 后续 JSON Mode 可直接使用的 schema 字符串
     */
    public static String schemaJson() {
        return SCHEMA_JSON;
    }

    /**
     * 返回结构化提取阶段要求的固定字段列表。
     *
     * @return 稳定字段名列表
     */
    public static List<String> requiredFields() {
        return REQUIRED_FIELDS;
    }

    /**
     * 校验 JSON Mode Prompt 是否满足官方关键词约束。
     *
     * @param prompt 上游 Prompt 正文
     * @return 受控校验结果，不直接抛出异常，便于后续 provider 显式分支处理
     */
    public static PromptValidationResult validateJsonModePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return PromptValidationResult.failed("JSON Mode Prompt 不能为空，且必须显式包含 JSON 关键词");
        }
        if (!prompt.contains(JSON_KEYWORD)) {
            return PromptValidationResult.failed("JSON Mode Prompt 必须显式包含 JSON 关键词，否则不能安全开启 json_object 响应格式");
        }
        return PromptValidationResult.passed();
    }

    /**
     * 解析结构化提取 JSON 响应。
     * <p>
     * 该方法返回受控结果对象而不是直接抛出 Jackson 异常，便于后续 provider 在“畸形 JSON / 缺字段 / 类型不符”场景下统一进入
     * `FAILED` 或 `PENDING` 分支，而不是把解析异常直接冒泡到控制层。
     *
     * @param rawJson 上游模型返回的原始 JSON 文本
     * @return 解析成功或失败的稳定结果对象
     */
    public static ParseResult parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return ParseResult.parseFailed("结构化提取结果为空，无法解析 JSON 对象");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            if (!root.isObject()) {
                return ParseResult.parseFailed("结构化提取结果必须是 JSON 对象");
            }

            List<String> missingRequiredFields = REQUIRED_FIELDS.stream()
                    .filter(fieldName -> !root.has(fieldName))
                    .toList();
            if (!missingRequiredFields.isEmpty()) {
                return ParseResult.parseFailed("结构化提取结果缺少必填字段: " + String.join(", ", missingRequiredFields));
            }

            StructuredExtraction extraction = new StructuredExtraction(
                    readRequiredIntent(root.get("intent")),
                    readRequiredConfidence(root.get("intentConfidence")),
                    readNullableText(root.get("toolName"), "toolName"),
                    readToolArguments(root.get("toolArguments")),
                    readMissingFields(root.get("missingFields")),
                    readNullableText(root.get("replyHint"), "replyHint"),
                    readNullableText(root.get("resolvedDeviceId"), "resolvedDeviceId"),
                    readNullableText(root.get("resolvedReservationId"), "resolvedReservationId"));
            return ParseResult.parsed(extraction);
        } catch (JsonProcessingException exception) {
            return ParseResult.parseFailed("结构化提取结果不是合法 JSON，当前不能直接进入工具调用阶段");
        } catch (IllegalArgumentException exception) {
            return ParseResult.parseFailed(exception.getMessage());
        }
    }

    private static String readRequiredIntent(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("字段 intent 必须是非空字符串");
        }
        String intent = node.asText().trim();
        boolean supportedIntent = List.of("RESERVE", "QUERY", "CANCEL", "HELP", "UNKNOWN").contains(intent);
        if (!supportedIntent) {
            throw new IllegalArgumentException("字段 intent 必须使用固定 AI 意图枚举");
        }
        return intent;
    }

    private static BigDecimal readRequiredConfidence(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("字段 intentConfidence 必须是数值");
        }
        return node.decimalValue();
    }

    private static String readNullableText(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("字段 " + fieldName + " 必须是字符串或 null");
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Map<String, Object> readToolArguments(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("字段 toolArguments 必须是 JSON 对象");
        }
        Map<String, Object> converted = OBJECT_MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        return Collections.unmodifiableMap(converted);
    }

    private static List<String> readMissingFields(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("字段 missingFields 必须是字符串数组");
        }
        List<String> values = OBJECT_MAPPER.convertValue(node, new TypeReference<List<String>>() {
        });
        boolean hasBlankValue = values.stream().anyMatch(value -> value == null || value.isBlank());
        if (hasBlankValue) {
            throw new IllegalArgumentException("字段 missingFields 只能包含非空字符串");
        }
        return List.copyOf(values);
    }

    /**
     * 结构化提取后的稳定结果对象。
     *
     * @param intent 固定五类 AI 意图之一
     * @param intentConfidence 意图识别置信度
     * @param toolName 后续建议执行的工具名，未知时可为 null
     * @param toolArguments 工具参数对象，未确定参数时应返回空对象而非缺字段
     * @param missingFields 仍需用户补充的字段列表
     * @param replyHint 给下游反馈模板或服务层使用的回复提示
     * @param resolvedDeviceId 唯一解析出的设备 ID，未命中时为 null
     * @param resolvedReservationId 唯一解析出的预约 ID，未命中时为 null
     */
    public record StructuredExtraction(
            String intent,
            BigDecimal intentConfidence,
            String toolName,
            Map<String, Object> toolArguments,
            List<String> missingFields,
            String replyHint,
            String resolvedDeviceId,
            String resolvedReservationId) {

        public StructuredExtraction {
            toolArguments = Collections.unmodifiableMap(new LinkedHashMap<>(toolArguments));
            missingFields = List.copyOf(missingFields);
        }
    }

    /**
     * Prompt 关键词校验结果。
     *
     * @param valid 是否通过校验
     * @param failureReason 校验失败原因，通过时为 null
     */
    public record PromptValidationResult(boolean valid, String failureReason) {

        private static PromptValidationResult passed() {
            return new PromptValidationResult(true, null);
        }

        private static PromptValidationResult failed(String failureReason) {
            return new PromptValidationResult(false, failureReason);
        }
    }

    /**
     * JSON 解析结果。
     *
     * @param success 是否解析成功
     * @param payload 解析成功后的结构化对象，失败时为 null
     * @param failureReason 失败原因，成功时为 null
     */
    public record ParseResult(boolean success, StructuredExtraction payload, String failureReason) {

        private static ParseResult parsed(StructuredExtraction payload) {
            return new ParseResult(true, payload, null);
        }

        private static ParseResult parseFailed(String failureReason) {
            return new ParseResult(false, null, failureReason);
        }
    }
}
