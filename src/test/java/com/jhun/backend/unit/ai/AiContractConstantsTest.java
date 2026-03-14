package com.jhun.backend.unit.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.common.enums.PromptTemplateType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * AI 与 Prompt 固定口径测试。
 * <p>
 * 该测试用于锁定 Task 13 的固定业务口径必须由集中枚举承载，避免意图、模板类型和执行结果继续散落为字符串字面量。
 */
class AiContractConstantsTest {

    /**
     * 验证 AI 意图枚举完整覆盖五种固定口径。
     */
    @Test
    void shouldExposeAllAllowedAiIntents() {
        assertEquals(EnumSet.of(
                        AiIntentType.RESERVE,
                        AiIntentType.QUERY,
                        AiIntentType.CANCEL,
                        AiIntentType.HELP,
                        AiIntentType.UNKNOWN),
                EnumSet.allOf(AiIntentType.class));
    }

    /**
     * 验证 Prompt 模板类型枚举完整覆盖四种固定口径。
     */
    @Test
    void shouldExposeAllAllowedPromptTemplateTypes() {
        assertEquals(EnumSet.of(
                        PromptTemplateType.INTENT_RECOGNITION,
                        PromptTemplateType.INFO_EXTRACTION,
                        PromptTemplateType.RESULT_FEEDBACK,
                        PromptTemplateType.CONFLICT_RECOMMENDATION),
                EnumSet.allOf(PromptTemplateType.class));
    }

    /**
     * 验证 AI 执行结果枚举完整覆盖成功、失败与待处理三种口径。
     */
    @Test
    void shouldExposeAllAllowedAiExecuteResults() {
        assertEquals(EnumSet.of(
                        AiExecuteResult.SUCCESS,
                        AiExecuteResult.FAILED,
                        AiExecuteResult.PENDING),
                EnumSet.allOf(AiExecuteResult.class));
        assertTrue(AiExecuteResult.PENDING.name().startsWith("PEND"));
    }
}
