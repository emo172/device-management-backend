package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.jhun.backend.service.support.ai.QwenExtractionSchema;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Qwen 结构化提取 Schema 测试。
 * <p>
 * 该测试锁定 task 4 交付的结构化提取真相源，确保字段集合、JSON Mode 关键词约束与解析失败分支都具备稳定行为。
 */
class QwenExtractionSchemaTest {

    /**
     * 验证统一 schema 至少覆盖计划要求的八个关键字段，避免后续 provider 各自维护不同字段口径。
     */
    @Test
    void shouldExposeStableRequiredStructuredExtractionFields() {
        assertThat(QwenExtractionSchema.requiredFields()).containsExactly(
                "intent",
                "intentConfidence",
                "toolName",
                "toolArguments",
                "missingFields",
                "replyHint",
                "resolvedDeviceId",
                "resolvedReservationId");
        assertThat(QwenExtractionSchema.schemaJson())
                .contains("\"intent\"")
                .contains("\"toolArguments\"")
                .contains("\"resolvedReservationId\"");
    }

    /**
     * 验证合法 JSON 响应可以被解析为稳定对象，供后续 provider 与工具调用层直接复用。
     */
    @Test
    void shouldParseValidStructuredExtractionPayload() {
        QwenExtractionSchema.ParseResult parseResult = QwenExtractionSchema.parse("""
                {
                  "intent": "CANCEL",
                  "intentConfidence": 0.91,
                  "toolName": "cancel_my_reservation",
                  "toolArguments": {
                    "reservationId": "res-1"
                  },
                  "missingFields": [],
                  "replyHint": "已拿到预约编号，可以进入取消工具。",
                  "resolvedDeviceId": "dev-1",
                  "resolvedReservationId": "res-1"
                }
                """);

        assertThat(parseResult.success()).isTrue();
        assertThat(parseResult.failureReason()).isNull();
        assertThat(parseResult.payload()).isNotNull();
        assertThat(parseResult.payload().intent()).isEqualTo("CANCEL");
        assertThat(parseResult.payload().intentConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.91));
        assertThat(parseResult.payload().toolName()).isEqualTo("cancel_my_reservation");
        assertThat(parseResult.payload().toolArguments()).containsEntry("reservationId", "res-1");
        assertThat(parseResult.payload().missingFields()).isEmpty();
        assertThat(parseResult.payload().resolvedReservationId()).isEqualTo("res-1");
    }

    /**
     * 验证畸形 JSON 不会直接抛出未捕获异常，而是进入可供后续 provider 判定的受控失败分支。
     */
    @Test
    void shouldRejectMalformedJsonWithControlledFailure() {
        QwenExtractionSchema.ParseResult parseResult = QwenExtractionSchema.parse("{not-json");

        assertThat(parseResult.success()).isFalse();
        assertThat(parseResult.payload()).isNull();
        assertThat(parseResult.failureReason()).contains("不是合法 JSON");
    }

    /**
     * 验证缺少 JSON 关键词的 Prompt 会被提前拦截，保护后续 JSON Mode 请求满足官方约束。
     */
    @Test
    void shouldRejectPromptMissingJsonKeywordForJsonMode() {
        QwenExtractionSchema.PromptValidationResult validationResult =
                QwenExtractionSchema.validateJsonModePrompt("请返回结构化对象，不需要多余解释。");

        assertThat(validationResult.valid()).isFalse();
        assertThat(validationResult.failureReason()).contains("JSON");
    }

    /**
     * 验证当上游返回缺字段 JSON 时，解析结果会明确指出缺失字段，而不是留给下游业务层做隐式兜底。
     */
    @Test
    void shouldRejectMissingRequiredFieldsWithControlledFailure() {
        QwenExtractionSchema.ParseResult parseResult = QwenExtractionSchema.parse("""
                {
                  "intent": "QUERY",
                  "intentConfidence": 0.86,
                  "toolName": null,
                  "missingFields": []
                }
                """);

        assertThat(parseResult.success()).isFalse();
        assertThat(parseResult.failureReason()).contains("toolArguments").contains("replyHint");
    }
}
