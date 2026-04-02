package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.entity.PromptTemplate;
import com.jhun.backend.mapper.PromptTemplateMapper;
import com.jhun.backend.service.support.ai.PromptTemplateRuntimeDefinition;
import com.jhun.backend.service.support.ai.PromptTemplateSupport;
import com.jhun.backend.service.support.ai.QwenStructuredExtractionPrompt;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * PromptTemplateSupport 运行时契约测试。
 * <p>
 * 该测试用于锁定 task 4 新增的 Prompt 编排真相源：四类模板都必须有明确运行时职责，
 * 结构化提取阶段必须同时消费 `INTENT_RECOGNITION` 与 `INFO_EXTRACTION`，并显式带出单轮策略与 JSON Mode 约束。
 */
class PromptTemplateSupportTest {

    private PromptTemplateMapper promptTemplateMapper;

    private PromptTemplateSupport promptTemplateSupport;

    @BeforeEach
    void setUp() {
        promptTemplateMapper = mock(PromptTemplateMapper.class);
        when(promptTemplateMapper.findActiveByType(anyString())).thenReturn(List.of());
        promptTemplateSupport = new PromptTemplateSupport(promptTemplateMapper, new DefaultResourceLoader());
    }

    /**
     * 验证四类 Prompt 都存在明确运行时定义，避免 `INFO_EXTRACTION` 再次退化成仅存在于枚举中的占位概念。
     */
    @Test
    void shouldExposeExplicitRuntimeDefinitionsForAllPromptTypes() {
        PromptTemplateRuntimeDefinition intentDefinition = promptTemplateSupport.resolveRuntimeDefinition(
                PromptTemplateType.INTENT_RECOGNITION);
        PromptTemplateRuntimeDefinition extractionDefinition = promptTemplateSupport.resolveRuntimeDefinition(
                PromptTemplateType.INFO_EXTRACTION);
        PromptTemplateRuntimeDefinition resultDefinition = promptTemplateSupport.resolveRuntimeDefinition(
                PromptTemplateType.RESULT_FEEDBACK);
        PromptTemplateRuntimeDefinition conflictDefinition = promptTemplateSupport.resolveRuntimeDefinition(
                PromptTemplateType.CONFLICT_RECOMMENDATION);

        assertThat(intentDefinition.runtimeRole()).contains("缺失字段");
        assertThat(extractionDefinition.runtimeRole()).contains("JSON Schema");
        assertThat(resultDefinition.runtimeRole()).contains("结果反馈");
        assertThat(conflictDefinition.runtimeRole()).contains("取消失败");
        assertThat(intentDefinition.conversationStrategy()).contains("单轮输入").contains("不拼接任何历史对话");
        assertThat(intentDefinition.sessionIdPolicy()).contains("本地历史归组").contains("上游模型");
        assertThat(extractionDefinition.jsonModeRequired()).isTrue();
        assertThat(resultDefinition.jsonModeRequired()).isFalse();
    }

    /**
     * 验证结构化提取 Prompt 会同时消费意图识别模板、信息提取模板与统一 schema，并自动满足 JSON Mode 关键词要求。
     */
    @Test
    void shouldBuildStructuredExtractionPromptWithJsonKeywordAndSingleTurnPolicy() {
        QwenStructuredExtractionPrompt prompt = promptTemplateSupport.resolveStructuredExtractionPrompt("帮我取消预约 RES-1");

        assertThat(prompt.requiresJsonMode()).isTrue();
        assertThat(prompt.upstreamPrompt()).contains("JSON");
        assertThat(prompt.upstreamPrompt()).contains("只分析当前这一轮用户输入");
        assertThat(prompt.upstreamPrompt()).contains("不拼接任何历史对话");
        assertThat(prompt.upstreamPrompt()).contains("帮我取消预约 RES-1");
        assertThat(prompt.intentTemplate()).contains("固定五类意图");
        assertThat(prompt.infoExtractionTemplate()).contains("JSON Schema");
        assertThat(prompt.schemaJson()).contains("\"intent\"").contains("\"resolvedReservationId\"");
        assertThat(prompt.sessionIdPolicy()).contains("本地历史归组");
    }

    /**
     * 验证数据库启用模板仍然优先于默认资源模板，保证后台维护的正式模板可以覆盖默认文案。
     */
    @Test
    void shouldPreferDatabaseTemplateContentWhenResolvingRuntimeDefinition() {
        when(promptTemplateMapper.findActiveByType(PromptTemplateType.INFO_EXTRACTION.name()))
                .thenReturn(List.of(activeTemplate("数据库信息提取模板 JSON")));

        PromptTemplateRuntimeDefinition definition = promptTemplateSupport.resolveRuntimeDefinition(
                PromptTemplateType.INFO_EXTRACTION);

        assertThat(definition.templateContent()).isEqualTo("数据库信息提取模板 JSON");
    }

    /**
     * 验证结构化提取依赖的模板若存在多条启用脏数据，会沿用既有 fail-fast 语义而不是静默挑一条继续执行。
     */
    @Test
    void shouldFailFastWhenStructuredExtractionPromptDependsOnDirtyTemplateData() {
        when(promptTemplateMapper.findActiveByType(PromptTemplateType.INTENT_RECOGNITION.name()))
                .thenReturn(List.of(activeTemplate("模板 1"), activeTemplate("模板 2")));

        assertThatThrownBy(() -> promptTemplateSupport.resolveStructuredExtractionPrompt("我要预约设备"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一 Prompt 模板类型存在多条启用模板，请先清理脏数据");
    }

    private PromptTemplate activeTemplate(String content) {
        PromptTemplate template = new PromptTemplate();
        template.setContent(content);
        return template;
    }
}
