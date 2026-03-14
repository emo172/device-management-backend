package com.jhun.backend.dto.ai;

import java.time.LocalDateTime;

/**
 * Prompt 模板响应。
 *
 * @param id 模板 ID
 * @param name 模板名称
 * @param code 模板代码
 * @param content 模板正文
 * @param type 模板类型
 * @param description 模板描述
 * @param variables 模板变量说明 JSON
 * @param active 是否启用
 * @param version 版本号
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record PromptTemplateResponse(
        String id,
        String name,
        String code,
        String content,
        String type,
        String description,
        String variables,
        boolean active,
        String version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
