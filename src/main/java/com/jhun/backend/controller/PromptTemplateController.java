package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.ai.PromptTemplateRequest;
import com.jhun.backend.dto.ai.PromptTemplateResponse;
import com.jhun.backend.service.PromptTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板控制器。
 * <p>
 * 该控制器只对系统管理员开放，用于维护 AI 规则降级和未来 LLM 接入所需的模板资产；
 * 普通用户与设备管理员均不得访问，以避免把后台模板管理能力暴露到业务使用入口。
 */
@RestController
@RequestMapping("/api/ai/prompts")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 查询 Prompt 模板列表。
     * <p>
     * 该接口仅供系统管理员进入模板管理页时加载全部模板资产，普通用户和设备管理员会在类级授权处被拦截；
     * 当前返回值包含模板内容、启停状态与版本信息，便于后台直接进行模板巡检和后续编辑。
     *
     * @return 统一响应包装后的模板列表，不产生写入副作用
     */
    @GetMapping
    public Result<List<PromptTemplateResponse>> listTemplates() {
        return Result.success(promptTemplateService.listTemplates());
    }

    /**
     * 查询单个 Prompt 模板详情。
     * <p>
     * 该接口用于系统管理员在编辑前拉取指定模板的完整配置，参数中的模板 ID 必须对应现有记录；
     * 若模板不存在，将由服务层抛出业务异常，避免后台误编辑一个已经失效的模板标识。
     *
     * @param templateId 模板主键 ID，用于定位唯一模板记录
     * @return 统一响应包装后的模板详情，不修改数据库内容
     */
    @GetMapping("/{id}")
    public Result<PromptTemplateResponse> getTemplate(@PathVariable("id") String templateId) {
        return Result.success(promptTemplateService.getTemplate(templateId));
    }

    /**
     * 新增 Prompt 模板。
     * <p>
     * 只有系统管理员可以通过该接口创建新模板，控制层会先触发请求体验证，再交由服务层执行类型合法性、名称/代码唯一性校验；
     * 成功后会新增一条模板记录，为 AI 规则降级或未来真实 LLM 接入提供新的可配置 Prompt 资产。
     *
     * @param request 模板创建请求，包含名称、代码、正文、类型、启停状态和版本等配置
     * @return 统一响应包装后的新建模板信息，成功时会产生数据库写入副作用
     */
    @PostMapping
    public Result<PromptTemplateResponse> createTemplate(@Valid @RequestBody PromptTemplateRequest request) {
        return Result.success(promptTemplateService.createTemplate(request));
    }

    /**
     * 更新已有 Prompt 模板。
     * <p>
     * 该接口用于系统管理员调整模板正文、启停状态和版本号等配置，避免在未审查的情况下把模板管理能力开放给业务角色；
     * 服务层会校验目标模板是否存在以及更新后的名称、代码是否仍满足唯一约束，成功后会覆盖原模板配置。
     *
     * @param templateId 待更新模板的主键 ID
     * @param request 模板更新请求，承载新的模板配置内容
     * @return 统一响应包装后的更新后模板信息，成功时会产生数据库更新副作用
     */
    @PutMapping("/{id}")
    public Result<PromptTemplateResponse> updateTemplate(
            @PathVariable("id") String templateId,
            @Valid @RequestBody PromptTemplateRequest request) {
        return Result.success(promptTemplateService.updateTemplate(templateId, request));
    }

    /**
     * 删除 Prompt 模板。
     * <p>
     * 该接口仅允许系统管理员清理已停用模板；若模板仍处于启用状态，服务层会直接拒绝，
     * 避免把仍可能被规则降级链路读取的模板直接物理删除。
     *
     * @param templateId 待删除模板主键 ID
     * @return 统一响应包装后的空结果，成功时会产生数据库删除副作用
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable("id") String templateId) {
        promptTemplateService.deleteTemplate(templateId);
        return Result.success(null);
    }
}
