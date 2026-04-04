package com.jhun.backend.service.support.ai;

import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.entity.PromptTemplate;
import com.jhun.backend.mapper.PromptTemplateMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Prompt 模板读取支持组件。
 * <p>
 * 当前阶段优先读取数据库中的启用模板；若测试环境或初始化阶段未配置对应模板，则回退到资源目录下的默认模板文件，
 * 从而保证规则降级 provider 不依赖真实 LLM 或外部配置中心也能稳定工作。
 */
@Component
public class PromptTemplateSupport {

    /**
     * 上游 Prompt 统一采用单轮输入策略。
     * <p>
     * Task 4 已明确禁止把历史对话拼接进发往 Qwen 的上下文，因此该说明需要作为后续 provider 的正式真相源保留在 support 层，
     * 避免后续实现者把 `sessionId` 误解成可直接发送给上游模型的多轮对话标识。
     */
    public static final String SINGLE_TURN_CONVERSATION_STRATEGY = "上游固定采用单轮输入策略，只分析当前这一轮用户输入，不拼接任何历史对话。";

    /**
     * `sessionId` 的正式运行时语义。
     * <p>
     * `sessionId` 只用于本地 `chat_history` 归组与历史页展示，不能作为上游模型的多轮上下文，也不能驱动自动拼接历史消息。
     */
    public static final String SESSION_ID_USAGE_POLICY = "sessionId 仅用于本地历史归组，不会作为多轮上下文发送给上游模型。";

    /**
     * 四类 Prompt 的正式运行时职责。
     * <p>
     * 这里显式收口每种模板在后续 Qwen provider 链路中的作用，避免 `INFO_EXTRACTION` 再次退化成“只有枚举和模板文件、没有真实消费入口”的占位概念。
     */
    private static final Map<PromptTemplateType, String> RUNTIME_ROLES = Map.of(
            PromptTemplateType.INTENT_RECOGNITION, "识别固定五类意图边界，并判断后续工具调用仍缺哪些缺失字段。",
            PromptTemplateType.INFO_EXTRACTION, "按统一 JSON Schema 输出结构化字段，作为后续 provider、工具调用与落库语义的正式输入。",
            PromptTemplateType.RESULT_FEEDBACK, "在成功执行或仅需补充少量信息时生成可直接展示的结果反馈。",
            PromptTemplateType.CONFLICT_RECOMMENDATION, "在业务冲突、取消失败或拒绝执行时生成原因说明与下一步建议。");

    private static final Map<PromptTemplateType, String> TEMPLATE_PATHS = Map.of(
            PromptTemplateType.INTENT_RECOGNITION, "classpath:templates/ai/default-intent-recognition.txt",
            PromptTemplateType.INFO_EXTRACTION, "classpath:templates/ai/default-info-extraction.txt",
            PromptTemplateType.RESULT_FEEDBACK, "classpath:templates/ai/default-result-feedback.txt",
            PromptTemplateType.CONFLICT_RECOMMENDATION, "classpath:templates/ai/default-conflict-recommendation.txt");

    private final PromptTemplateMapper promptTemplateMapper;
    private final ResourceLoader resourceLoader;

    public PromptTemplateSupport(PromptTemplateMapper promptTemplateMapper, ResourceLoader resourceLoader) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 读取某类模板内容。
     * <p>
     * 运行时首先检查数据库中该类型是否存在多条启用模板；若有，则直接 fail-fast 抛出业务异常，
     * 避免继续依赖排序规则静默选中某一条模板造成行为歧义。仅当数据库中没有启用模板时，才回退到资源目录默认模板。
     *
     * @param type 模板类型
     * @return 当前生效模板内容
     */
    public String resolveActiveTemplateContent(PromptTemplateType type) {
        List<PromptTemplate> activeTemplates = promptTemplateMapper.findActiveByType(type.name());
        if (activeTemplates.size() > 1) {
            throw new BusinessException("同一 Prompt 模板类型存在多条启用模板，请先清理脏数据");
        }
        if (!activeTemplates.isEmpty()) {
            PromptTemplate template = activeTemplates.getFirst();
            if (template.getContent() != null && !template.getContent().isBlank()) {
                return template.getContent();
            }
        }
        return loadFallback(type);
    }

    /**
     * 解析某类 Prompt 的正式运行时定义。
     * <p>
     * 该方法返回的不只是模板正文，还会带上“运行时职责 / 单轮策略 / sessionId 使用边界 / 是否要求 JSON Mode”这些元信息，
     * 供后续 Qwen provider 直接复用，而不必在 provider 层重新硬编码另一套 Prompt 语义。
     *
     * @param type 模板类型
     * @return 当前模板的运行时定义
     */
    public PromptTemplateRuntimeDefinition resolveRuntimeDefinition(PromptTemplateType type) {
        return new PromptTemplateRuntimeDefinition(
                type,
                resolveActiveTemplateContent(type),
                RUNTIME_ROLES.getOrDefault(type, "当前模板类型尚未定义正式运行时职责。"),
                PromptTemplateType.INFO_EXTRACTION.equals(type),
                SINGLE_TURN_CONVERSATION_STRATEGY,
                SESSION_ID_USAGE_POLICY);
    }

    /**
     * 组装首发阶段的结构化提取 Prompt。
     * <p>
     * 根据 task 4 约束，结构化提取阶段必须同时消费 `INTENT_RECOGNITION` 与 `INFO_EXTRACTION` 两类模板：
     * 前者负责意图边界与缺参判断，后者负责严格 JSON 提取；最终输出只允许依据当前这一轮用户输入，
     * 并显式携带 JSON 关键词与统一 schema，供后续 Qwen JSON Mode 直接复用。
     *
     * @param userMessage 当前这一轮用户输入
     * @return 可供后续 provider 直接使用的结构化提取 Prompt 规格
     */
    public QwenStructuredExtractionPrompt resolveStructuredExtractionPrompt(String userMessage) {
        PromptTemplateRuntimeDefinition intentDefinition = resolveRuntimeDefinition(PromptTemplateType.INTENT_RECOGNITION);
        PromptTemplateRuntimeDefinition extractionDefinition = resolveRuntimeDefinition(PromptTemplateType.INFO_EXTRACTION);
        String normalizedMessage = userMessage == null ? "" : userMessage.trim();
        String schemaJson = QwenExtractionSchema.schemaJson();
        String upstreamPrompt = """
                你正在处理智能设备管理系统的单轮结构化提取任务。
                最终输出必须是一个 JSON 对象，并且只能依据当前这一轮用户输入完成判断。
                %s
                %s

                【%s】
                %s

                【%s】
                %s

                【结构化提取 JSON Schema】
                %s

                【当前用户输入】
                %s
                """.formatted(
                SINGLE_TURN_CONVERSATION_STRATEGY,
                SESSION_ID_USAGE_POLICY,
                intentDefinition.type().name(),
                intentDefinition.templateContent(),
                extractionDefinition.type().name(),
                extractionDefinition.templateContent(),
                schemaJson,
                normalizedMessage);
        return new QwenStructuredExtractionPrompt(
                intentDefinition.templateContent(),
                extractionDefinition.templateContent(),
                schemaJson,
                upstreamPrompt,
                SINGLE_TURN_CONVERSATION_STRATEGY,
                SESSION_ID_USAGE_POLICY);
    }

    private String loadFallback(PromptTemplateType type) {
        String path = TEMPLATE_PATHS.get(type);
        if (path == null) {
            return "当前未配置模板";
        }

        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            return "当前未配置模板";
        }

        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return "当前未配置模板";
        }
    }
}
