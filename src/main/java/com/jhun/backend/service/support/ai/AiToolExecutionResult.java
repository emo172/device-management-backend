package com.jhun.backend.service.support.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 工具执行稳定结果。
 * <p>
 * 该结果对象同时服务两个边界：
 * 1) 当前任务需要对未知工具、缺参、越权和业务拒绝返回可预测的受控结果；
 * 2) 后续 task 6 需要把执行结果作为 Qwen tool message 的稳定输入，因此这里提前固化结构化字段与序列化文本。
 * <p>
 * 注意：这里的 {@code payload} 必须只包含稳定、可序列化的基础结构，不能直接把底层异常、Mapper 结果或 Entity 暴露给 LLM 层。
 *
 * @param toolName 当前执行的工具名；未知工具时保留原始名称，便于后续反馈提示
 * @param success 工具是否执行成功
 * @param errorCode 失败编码；成功时为 {@code null}
 * @param message 对模型与后续总结都稳定可读的结果说明
 * @param payload 稳定输出数据，成功和失败都统一保留对象结构
 * @param serializedResult 供后续 Qwen summarization 直接复用的 JSON 字符串
 */
public record AiToolExecutionResult(
        String toolName,
        boolean success,
        String errorCode,
        String message,
        Map<String, Object> payload,
        String serializedResult) {

    public AiToolExecutionResult {
        toolName = toolName == null ? null : toolName.trim();
        message = message == null ? "" : message;
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        serializedResult = serializedResult == null ? "" : serializedResult;
    }
}
