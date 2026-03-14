package com.jhun.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI 对话请求。
 * <p>
 * `sessionId` 允许前端在多轮对话中透传，但必须与 DDL 中 `VARCHAR(36)` 的会话字段长度保持一致；
 * `message` 必填且不能为空，避免写入无意义历史记录。
 *
 * @param sessionId 对话会话 ID，可为空
 * @param message 用户输入文本
 */
public record AiChatRequest(
        @Pattern(
                        regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                        message = "会话 ID 必须是 UUID 格式")
        @Size(max = 36, message = "会话 ID 长度不能超过 36 个字符") String sessionId,
        @NotBlank(message = "对话内容不能为空") String message) {
}
