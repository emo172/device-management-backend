package com.jhun.backend.service.support.ai.qwen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen OpenAI 兼容 Chat Completions 请求 DTO。
 * <p>
 * 该对象只暴露当前计划真正需要的调用维度：普通消息、JSON Mode、tools 与单轮禁止并行工具调用。
 * 结构化提取阶段额外需要显式控制 `enable_thinking`，以满足计划对“JSON Mode + 非思考模式”组合的合规要求。
 * `model` 不由调用方填写，而是由客户端从 `AiRuntimeProperties` 注入正式运行时模型，
 * 避免上层误传与配置真相源不一致的模型名。
 *
 * @param messages 对话消息列表，当前首发仅使用文本消息与后续工具结果消息所需的最小字段
 * @param responseFormat JSON Mode 配置；为空表示普通文本对话
 * @param tools 本轮可用工具列表；为空表示不启用 Function Calling
 * @param parallelToolCalls 是否允许并行工具调用；本计划固定要求为 `false`
 * @param enableThinking 是否显式开启思考模式；仅在需要覆盖模型默认值时传入
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QwenOpenAiChatCompletionRequest(
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        List<Tool> tools,
        @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
        @JsonProperty("enable_thinking") Boolean enableThinking) {

    public QwenOpenAiChatCompletionRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
    }

    public QwenOpenAiChatCompletionRequest(
            List<Message> messages,
            ResponseFormat responseFormat,
            List<Tool> tools,
            Boolean parallelToolCalls) {
        this(messages, responseFormat, tools, parallelToolCalls, null);
    }

    /**
     * 最小消息 DTO。
     * <p>
     * 当前保留 `role`、`content` 与 `tool_call_id` 三个字段：
     * - 普通对话 / JSON Mode 只需要 `role + content`；
     * - 后续工具结果回传需要 `tool_call_id` 绑定到指定调用；
     * - 本任务不提前实现多模态内容块，因此不引入更复杂的 content 数组模型。
     *
     * @param role OpenAI 兼容角色，如 `system`、`user`、`assistant`、`tool`
     * @param content 文本内容；工具调用响应消息可为空
     * @param toolCallId 工具结果消息关联的 tool call ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            String role,
            String content,
            @JsonProperty("tool_call_id") String toolCallId) {
    }

    /**
     * JSON Mode 配置。
     * <p>
     * 当前正式支持值固定为 `json_object`；
     * 是否在 Prompt 中包含 `JSON` 关键字属于上层 Prompt 编排责任，不在客户端层重复校验。
     *
     * @param type 响应格式类型
     */
    public record ResponseFormat(String type) {
    }

    /**
     * 工具定义 DTO。
     *
     * @param type 工具类型，当前固定为 `function`
     * @param function 函数工具定义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(String type, FunctionDefinition function) {
    }

    /**
     * 函数工具定义。
     * <p>
     * `parameters` 采用 `Map<String, Object>` 作为正式承载形态，避免在 provider/client 代码中手写 JSON 字符串，
     * 同时保留后续工具注册阶段对 schema 做结构化装配的空间；此外仍兼容 `JsonNode` 入参，
     * 这样既能复用现有测试辅助方法，也不会把 JSON Schema 组装责任散落到调用方。
     *
     * @param name 工具名称
     * @param description 工具说明
     * @param parameters 函数参数 JSON Schema
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDefinition(String name, String description, Map<String, Object> parameters) {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        public FunctionDefinition {
            parameters = parameters == null ? null : Map.copyOf(parameters);
        }

        public FunctionDefinition(String name, String description, JsonNode parameters) {
            this(name, description, parameters == null
                    ? null
                    : OBJECT_MAPPER.convertValue(parameters, new TypeReference<LinkedHashMap<String, Object>>() {
                    }));
        }
    }
}
