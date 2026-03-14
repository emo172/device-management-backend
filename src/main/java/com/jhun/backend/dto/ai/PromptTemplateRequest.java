package com.jhun.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Prompt 模板写入请求。
 * <p>
 * 该请求同时用于创建和更新模板，`active` 映射到表字段 `is_active`，避免控制层直接暴露数据库命名细节；
 * 名称、代码、类型、描述和版本长度需要与 DDL 字段长度对齐，防止非法输入进入服务层后再触发数据库异常。
 *
 * @param name 模板名称
 * @param code 模板代码
 * @param content 模板正文
 * @param type 模板类型
 * @param description 模板描述
 * @param variables 模板变量说明 JSON
 * @param active 是否启用
 * @param version 版本号
 */
public record PromptTemplateRequest(
        @NotBlank(message = "模板名称不能为空") @Size(max = 100, message = "模板名称长度不能超过 100 个字符") String name,
        @NotBlank(message = "模板代码不能为空") @Size(max = 50, message = "模板代码长度不能超过 50 个字符") String code,
        @NotBlank(message = "模板内容不能为空") String content,
        @Pattern(
                        regexp = "^\\s*(INTENT_RECOGNITION|INFO_EXTRACTION|RESULT_FEEDBACK|CONFLICT_RECOMMENDATION)\\s*$",
                        message = "模板类型必须是固定枚举值")
        @NotBlank(message = "模板类型不能为空") @Size(max = 50, message = "模板类型长度不能超过 50 个字符") String type,
        @Size(max = 500, message = "模板描述长度不能超过 500 个字符") String description,
        String variables,
        boolean active,
        @Pattern(regexp = "^\\s*[0-9]+(\\.[0-9]+){0,2}\\s*$", message = "模板版本必须使用数字版本格式，例如 1、1.0 或 1.0.0")
        @NotBlank(message = "模板版本不能为空") @Size(max = 20, message = "模板版本长度不能超过 20 个字符") String version) {
}
