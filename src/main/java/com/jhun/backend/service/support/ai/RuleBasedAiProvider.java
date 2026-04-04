package com.jhun.backend.service.support.ai;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.config.ai.AiProperties;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 规则降级 AI provider。
 * <p>
 * 该组件通过关键词规则完成最小意图识别和文本反馈，保证当前任务在未接入真实 LLM 的情况下仍能提供稳定的接口行为；
 * 同时它只返回建议与结构化结果，不直接写业务表，避免突破“AI 只能通过 Service 层复用能力”的边界。
 * 在新的 provider 抽象下，它继续承担 `mock` 模式的正式实现，`mock-rule-provider` 的返回语义不能被本次重构改变。
 */
@Component
public class RuleBasedAiProvider implements AiProvider {

    private final PromptTemplateSupport promptTemplateSupport;

    public RuleBasedAiProvider(PromptTemplateSupport promptTemplateSupport) {
        this.promptTemplateSupport = promptTemplateSupport;
    }

    @Override
    public AiProperties.Provider provider() {
        return AiProperties.Provider.MOCK;
    }

    /**
     * 按固定规则处理用户输入。
     *
     * @param userId 当前用户 ID；规则降级模式当前不消费该字段，但为统一 Provider 契约保留
     * @param role 当前角色；规则降级模式当前不消费该字段，但为统一 Provider 契约保留
     * @param message 用户输入
     * @return 规则识别结果
     */
    @Override
    public AiProviderResult process(String userId, String role, String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        AiIntentType intent = recognizeIntent(normalized);
        AiExecuteResult executeResult = resolveExecuteResult(intent);
        BigDecimal confidence = resolveConfidence(intent);
        String extractedInfo = "{\"message\":\"" + escapeJson(message == null ? "" : message.trim()) + "\"}";
        String aiResponse = buildResponse(intent, message == null ? "" : message.trim());
        String errorMessage = AiExecuteResult.FAILED.equals(executeResult) ? "未识别到可执行的固定意图" : null;
        return new AiProviderResult(
                intent.name(),
                confidence,
                extractedInfo,
                executeResult.name(),
                aiResponse,
                errorMessage,
                "mock-rule-provider",
                null,
                null);
    }

    private AiIntentType recognizeIntent(String normalized) {
        /*
         * 当前仅允许五种固定意图，优先按高置信关键词路由。
         * 这里故意保持规则简单，便于在真实 LLM 接入前稳定通过集成测试并避免误改业务表。
         */
        if (containsAny(normalized, "预约", "借用", "预定")) {
            return AiIntentType.RESERVE;
        }
        if (containsAny(normalized, "取消", "撤销", "不借了")) {
            return AiIntentType.CANCEL;
        }
        if (containsAny(normalized, "查询", "查看", "状态", "记录", "历史")) {
            return AiIntentType.QUERY;
        }
        if (containsAny(normalized, "帮助", "怎么", "如何", "说明")) {
            return AiIntentType.HELP;
        }
        return AiIntentType.UNKNOWN;
    }

    private AiExecuteResult resolveExecuteResult(AiIntentType intent) {
        return switch (intent) {
            case QUERY, HELP -> AiExecuteResult.SUCCESS;
            case RESERVE, CANCEL -> AiExecuteResult.PENDING;
            default -> AiExecuteResult.FAILED;
        };
    }

    private BigDecimal resolveConfidence(AiIntentType intent) {
        return switch (intent) {
            case RESERVE -> BigDecimal.valueOf(0.92);
            case QUERY -> BigDecimal.valueOf(0.90);
            case CANCEL -> BigDecimal.valueOf(0.91);
            case HELP -> BigDecimal.valueOf(0.88);
            default -> BigDecimal.valueOf(0.35);
        };
    }

    private String buildResponse(AiIntentType intent, String message) {
        String promptHint = switch (intent) {
            case RESERVE -> promptTemplateSupport.resolveActiveTemplateContent(PromptTemplateType.RESULT_FEEDBACK);
            case QUERY -> promptTemplateSupport.resolveActiveTemplateContent(PromptTemplateType.RESULT_FEEDBACK);
            case CANCEL -> promptTemplateSupport.resolveActiveTemplateContent(PromptTemplateType.CONFLICT_RECOMMENDATION);
            case HELP -> promptTemplateSupport.resolveActiveTemplateContent(PromptTemplateType.RESULT_FEEDBACK);
            default -> promptTemplateSupport.resolveActiveTemplateContent(PromptTemplateType.INTENT_RECOGNITION);
        };

        return switch (intent) {
            case RESERVE -> "已识别为预约诉求，当前为规则降级模式，请补充设备与时间信息后走正式预约接口。模板提示：" + promptHint;
            case QUERY -> "已识别为查询诉求，当前为规则降级模式，可继续补充设备编号、预约编号或时间范围。模板提示：" + promptHint;
            case CANCEL -> "已识别为取消诉求，当前未直接执行取消，请提供预约编号后由正式业务服务处理。模板提示：" + promptHint;
            case HELP -> "当前支持预约、查询、取消和帮助四类固定意图，未接入真实 LLM。模板提示：" + promptHint;
            default -> "暂未识别你的意图，请改用预约、查询、取消、帮助等更明确的描述。原始输入：" + message + "。模板提示：" + promptHint;
        };
    }

    private boolean containsAny(String normalized, String... keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
