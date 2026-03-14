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
