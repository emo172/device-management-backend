package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.dto.ai.PromptTemplateRequest;
import com.jhun.backend.dto.ai.PromptTemplateResponse;
import com.jhun.backend.entity.PromptTemplate;
import com.jhun.backend.mapper.PromptTemplateMapper;
import com.jhun.backend.service.PromptTemplateService;
import com.jhun.backend.util.UuidUtil;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt 模板服务实现。
 * <p>
 * 该服务负责系统管理员侧的模板查询与维护，并对模板类型、名称和代码做最小业务校验，
 * 避免 AI 降级 provider 读取到非法类型或歧义模板。
 */
@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private static final EnumSet<PromptTemplateType> ALLOWED_TYPES = EnumSet.allOf(PromptTemplateType.class);

    private final PromptTemplateMapper promptTemplateMapper;

    public PromptTemplateServiceImpl(PromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    /**
     * 查询全部 Prompt 模板。
     * <p>
     * 该方法服务于系统管理员模板管理页，返回值会覆盖模板正文、类型、启停状态和版本等关键配置；
     * 查询本身不修改任何模板数据，但其输出会被后台直接用于人工巡检和编辑入口展示。
     *
     * @return 模板响应列表，不产生数据库写入副作用
     */
    @Override
    public List<PromptTemplateResponse> listTemplates() {
        return promptTemplateMapper.findAllTemplates().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询指定 Prompt 模板详情。
     * <p>
     * 该方法用于系统管理员在编辑前回显模板当前配置，若目标模板不存在则立即抛出业务异常，
     * 避免后续编辑动作基于一个失效或错误的模板 ID 继续执行。
     *
     * @param templateId 模板主键 ID
     * @return 对应模板的完整响应对象
     */
    @Override
    public PromptTemplateResponse getTemplate(String templateId) {
        return toResponse(mustFindTemplate(templateId));
    }

    /**
     * 创建新的 Prompt 模板。
     * <p>
     * 创建前会先校验模板类型是否属于真相源允许的四种固定口径，并校验名称、代码在现有模板中必须唯一；
     * 创建成功后会新增一条模板记录，直接影响后续规则降级 provider 可读取到的模板资产。
     *
     * @param request 模板创建请求，包含正文、类型、变量说明、启停状态与版本信息
     * @return 新建后的模板响应对象
     */
    @Override
    @Transactional
    public PromptTemplateResponse createTemplate(PromptTemplateRequest request) {
        PromptTemplateRequest normalizedRequest = normalizeRequest(request);
        validateType(normalizedRequest.type());
        ensureUniqueNameAndCode(normalizedRequest.name(), normalizedRequest.code(), null);
        ensureSingleActiveTemplatePerType(normalizedRequest.type(), normalizedRequest.active(), null);

        PromptTemplate template = new PromptTemplate();
        template.setId(UuidUtil.randomUuid());
        applyRequest(template, normalizedRequest);
        promptTemplateMapper.insert(template);
        return toResponse(template);
    }

    /**
     * 更新已有 Prompt 模板。
     * <p>
     * 更新时会先确认目标模板存在，再复用模板类型口径校验与名称/代码唯一性校验；
     * 一旦更新成功，会覆盖原记录中的正文、启停状态和版本配置，因此属于直接影响运行时模板读取结果的写操作。
     *
     * @param templateId 待更新模板的主键 ID
     * @param request 模板更新请求，承载新的模板配置内容
     * @return 更新后的模板响应对象
     */
    @Override
    @Transactional
    public PromptTemplateResponse updateTemplate(String templateId, PromptTemplateRequest request) {
        PromptTemplateRequest normalizedRequest = normalizeRequest(request);
        validateType(normalizedRequest.type());
        PromptTemplate template = mustFindTemplate(templateId);
        ensureUniqueNameAndCode(normalizedRequest.name(), normalizedRequest.code(), templateId);
        ensureSingleActiveTemplatePerType(normalizedRequest.type(), normalizedRequest.active(), templateId);
        applyRequest(template, normalizedRequest);
        promptTemplateMapper.updateById(template);
        return toResponse(template);
    }

    /**
     * 删除 Prompt 模板。
     * <p>
     * 启用中的模板可能仍被 AI 规则降级链路读取，因此必须先停用再删除；
     * 只有目标模板存在且当前已停用时，才允许执行物理删除，防止后台误操作直接移除正在生效的模板资产。
     *
     * @param templateId 待删除模板主键 ID
     */
    @Override
    @Transactional
    public void deleteTemplate(String templateId) {
        PromptTemplate template = mustFindTemplate(templateId);
        if (template.getIsActive() != null && template.getIsActive() == 1) {
            throw new BusinessException("启用中的 Prompt 模板不能直接删除，请先停用后再删除");
        }
        promptTemplateMapper.deleteById(templateId);
    }

    /**
     * 校验模板类型是否符合固定业务口径。
     * <p>
     * Prompt 模板类型只能取 `INTENT_RECOGNITION`、`INFO_EXTRACTION`、`RESULT_FEEDBACK`、`CONFLICT_RECOMMENDATION` 四种；
     * 若放行任意字符串，会导致规则降级 provider 无法稳定按类型读取模板。
     *
     * @param type 请求中的模板类型
     */
    private void validateType(String type) {
        try {
            if (!ALLOWED_TYPES.contains(PromptTemplateType.valueOf(type))) {
                throw new BusinessException("Prompt 模板类型不合法");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("Prompt 模板类型不合法");
        }
    }

    /**
     * 归一化模板请求内容。
     * <p>
     * 创建和更新都必须先做首尾空白清理，再执行类型与唯一性校验，
     * 否则 `code + 空白`、`type + 空白` 这类输入会绕过业务规则或触发非预期错误。
     *
     * @param request 原始模板请求
     * @return 已完成关键字段归一化的新请求对象
     */
    private PromptTemplateRequest normalizeRequest(PromptTemplateRequest request) {
        return new PromptTemplateRequest(
                request.name().trim(),
                request.code().trim(),
                request.content().trim(),
                request.type().trim(),
                request.description() == null ? null : request.description().trim(),
                request.variables() == null ? null : request.variables().trim(),
                request.active(),
                request.version().trim());
    }

    private PromptTemplate mustFindTemplate(String templateId) {
        PromptTemplate template = promptTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("Prompt 模板不存在");
        }
        return template;
    }

    /**
     * 校验模板名称与模板代码的唯一性。
     * <p>
     * 后台创建和更新都必须保证名称、代码在全库内不冲突；
     * 更新场景允许当前模板继续保留自己的名称和代码，因此会通过 `currentTemplateId` 排除自身记录。
     *
     * @param name 待校验的模板名称
     * @param code 待校验的模板代码
     * @param currentTemplateId 当前更新模板 ID；创建场景传空
     */
    private void ensureUniqueNameAndCode(String name, String code, String currentTemplateId) {
        PromptTemplate byName = promptTemplateMapper.findByName(name);
        if (byName != null && !byName.getId().equals(currentTemplateId)) {
            throw new BusinessException("Prompt 模板名称已存在");
        }

        PromptTemplate byCode = promptTemplateMapper.findByCode(code);
        if (byCode != null && !byCode.getId().equals(currentTemplateId)) {
            throw new BusinessException("Prompt 模板代码已存在");
        }
    }

    /**
     * 校验同一模板类型同时只能存在一条启用模板。
     * <p>
     * 当本次写入请求希望把模板置为启用时，需要先检查同类型是否已有其他启用模板；
     * 若存在，则直接返回稳定业务错误，避免运行时依赖“按更新时间挑最新一条”这种隐式规则。
     *
     * @param type 归一化后的模板类型
     * @param active 本次写入后是否启用
     * @param currentTemplateId 当前更新模板 ID；创建场景传空
     */
    private void ensureSingleActiveTemplatePerType(String type, boolean active, String currentTemplateId) {
        if (!active) {
            return;
        }
        List<PromptTemplate> existingActiveTemplates = promptTemplateMapper.findActiveByType(type);
        boolean existsAnotherActiveTemplate = existingActiveTemplates.stream()
                .anyMatch(template -> !template.getId().equals(currentTemplateId));
        if (existsAnotherActiveTemplate) {
            throw new BusinessException("同一 Prompt 模板类型只能有一条启用模板");
        }
    }

    /**
     * 把请求内容映射回模板实体。
     * <p>
     * 该方法统一负责创建与更新场景的字段回填，并在写入前去除关键文本字段首尾空白；
     * 一旦调用方后续执行 `insert` 或 `updateById`，这些字段就会成为数据库中的最新模板配置。
     *
     * @param template 待写入的模板实体
     * @param request 控制层传入的模板请求
     */
    private void applyRequest(PromptTemplate template, PromptTemplateRequest request) {
        template.setName(request.name().trim());
        template.setCode(request.code().trim());
        template.setContent(request.content().trim());
        template.setType(request.type().trim());
        template.setDescription(request.description());
        template.setVariables(request.variables());
        template.setIsActive(request.active() ? 1 : 0);
        template.setVersion(request.version().trim());
    }

    private PromptTemplateResponse toResponse(PromptTemplate template) {
        return new PromptTemplateResponse(
                template.getId(),
                template.getName(),
                template.getCode(),
                template.getContent(),
                template.getType(),
                template.getDescription(),
                template.getVariables(),
                template.getIsActive() != null && template.getIsActive() == 1,
                template.getVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
