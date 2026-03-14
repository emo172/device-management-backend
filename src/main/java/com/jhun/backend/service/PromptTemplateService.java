package com.jhun.backend.service;

import com.jhun.backend.dto.ai.PromptTemplateRequest;
import com.jhun.backend.dto.ai.PromptTemplateResponse;
import java.util.List;

/**
 * Prompt 模板服务。
 */
public interface PromptTemplateService {

    /**
     * 查询全部 Prompt 模板。
     * <p>
     * 该方法服务于系统管理员后台，不向普通用户或设备管理员开放；
     * 返回结果需要覆盖模板正文、类型、启停状态和版本信息，供控制层直接回传列表页使用。
     *
     * @return 模板列表，只读操作，无数据库写入副作用
     */
    List<PromptTemplateResponse> listTemplates();

    /**
     * 查询单个 Prompt 模板详情。
     * <p>
     * 当系统管理员进入编辑页时，通过模板 ID 拉取完整模板配置；
     * 若目标模板不存在，应抛出业务异常阻止后续误编辑。
     *
     * @param templateId 模板主键 ID
     * @return 指定模板的完整响应对象
     */
    PromptTemplateResponse getTemplate(String templateId);

    /**
     * 创建 Prompt 模板。
     * <p>
     * 该方法为系统管理员新增模板资产提供入口，会执行模板类型合法性与名称/代码唯一性校验；
     * 创建成功后会新增一条模板记录，供 AI 规则降级和后续 LLM 接入读取。
     *
     * @param request 模板创建请求，包含正文、类型、启停状态和版本等配置
     * @return 新建成功后的模板响应对象
     */
    PromptTemplateResponse createTemplate(PromptTemplateRequest request);

    /**
     * 更新 Prompt 模板。
     * <p>
     * 该方法用于系统管理员维护既有模板，会校验目标模板是否存在以及更新后的名称、代码是否仍满足唯一约束；
     * 更新成功后会覆盖原记录，直接影响当前系统读取到的模板配置。
     *
     * @param templateId 待更新模板的主键 ID
     * @param request 模板更新请求，承载新的模板配置内容
     * @return 更新后的模板响应对象
     */
    PromptTemplateResponse updateTemplate(String templateId, PromptTemplateRequest request);
}
