package com.jhun.backend.service.support.ai.qwen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Qwen OpenAI 兼容 Chat Completions 响应 DTO。
 * <p>
 * 当前只保留普通回答、JSON Mode、tool_calls 和 usage 所需的最小字段，
 * 其余兼容接口元数据统一忽略，避免客户端为了本次任务引入过宽的响应模型。
 *
 * @param id 上游响应 ID，便于后续问题排查
 * @param model 本次调用实际使用的模型标识
 * @param choices 结果候选列表；若为空属于协议级异常而不是业务成功
 * @param usage Token 用量统计
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QwenOpenAiChatCompletionResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage) {

    public QwenOpenAiChatCompletionResponse {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /**
     * 候选结果。
     *
     * @param index 候选序号
     * @param message 助手消息体
     * @param finishReason 结束原因，例如 `stop` 或 `tool_calls`
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            Message message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    /**
     * 助手消息体。
     * <p>
     * 普通文本回答使用 `content`；Function Calling 则通过 `tool_calls` 返回建议执行的工具。
     * 两者可能互斥，因此客户端不在 DTO 层强行做“二选一”校验，而把最终协议完整性交给调用方和测试共同锁定。
     *
     * @param role 消息角色
     * @param content 助手文本内容，tool call 场景允许为空
     * @param toolCalls 工具调用列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls) {

        public Message {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    /**
     * 工具调用结果。
     *
     * @param id 工具调用 ID
     * @param type 调用类型，当前固定为 `function`
     * @param function 被调用函数及参数字符串
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(String id, String type, FunctionCall function) {
    }

    /**
     * 函数调用内容。
     * <p>
     * `arguments` 在 OpenAI 兼容协议中本身就是 JSON 字符串，后续是否继续解析成结构化参数由更上层决定，
     * 客户端层只负责保真转出，避免在本任务提前引入工具执行语义。
     *
     * @param name 函数名称
     * @param arguments JSON 字符串形式的函数参数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionCall(String name, String arguments) {
    }

    /**
     * Token 用量统计。
     *
     * @param promptTokens 提示词 token 数
     * @param completionTokens 输出 token 数
     * @param totalTokens 总 token 数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
