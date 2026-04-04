package com.jhun.backend.service.support.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 工具注册表。
 * <p>
 * 该注册表是任务 5 中“AI 只能调用四个正式工具”的唯一真相源：
 * 后续无论是 Qwen provider 生成 tools 定义，还是执行层做未知工具拦截，
 * 都必须先经过这里，而不是在多个类里散落硬编码工具名。
 */
@Component
public class AiToolRegistry {

    public static final String QUERY_DEVICE_AVAILABILITY = "query_device_availability";
    public static final String QUERY_MY_RESERVATIONS = "query_my_reservations";
    public static final String CREATE_MY_RESERVATION = "create_my_reservation";
    public static final String CANCEL_MY_RESERVATION = "cancel_my_reservation";

    /**
     * 工具声明真相源。
     * <p>
     * Task 6 的 Function Calling 不能在 Provider 里重新硬编码一套工具描述与参数 Schema，
     * 否则“执行白名单”和“上游声明白名单”会再次漂移。
     * 因此这里把工具名、中文职责说明和 JSON Schema 一并收口，供 Provider 生成 tools 请求时直接复用。
     */
    private static final Map<String, ToolDefinition> TOOL_DEFINITIONS = createToolDefinitions();

    private static final List<String> SUPPORTED_TOOL_NAMES = List.copyOf(TOOL_DEFINITIONS.keySet());

    /**
     * 返回当前正式开放的工具名列表。
     * <p>
     * 返回值固定为四项且按稳定顺序输出，避免后续 provider 构造 tools 数组时出现顺序漂移。
     */
    public List<String> supportedToolNames() {
        return SUPPORTED_TOOL_NAMES;
    }

    /**
     * 返回指定工具的正式声明。
     * <p>
     * 若工具不存在，返回 {@code null} 让上层继续走统一受控失败分支，避免这里抛异常打断 provider 编排。
     */
    public ToolDefinition definitionOf(String toolName) {
        if (toolName == null) {
            return null;
        }
        return TOOL_DEFINITIONS.get(toolName.trim());
    }

    /**
     * 判断给定工具是否属于正式白名单。
     * <p>
     * 这里使用显式白名单而不是“名称不为空就执行”，是为了防止模型幻觉出仓库中并不存在的工具名后直接落入未定义分支。
     */
    public boolean supports(String toolName) {
        return toolName != null && SUPPORTED_TOOL_NAMES.contains(toolName.trim());
    }

    private static Map<String, ToolDefinition> createToolDefinitions() {
        Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
        definitions.put(
                QUERY_DEVICE_AVAILABILITY,
                new ToolDefinition(
                        QUERY_DEVICE_AVAILABILITY,
                        "查询当前登录用户可见的设备可用性，可按设备 ID、设备编号、设备名称或设备分类筛选。",
                        schema(
                                properties(
                                        property("deviceId", "string", "设备 ID，已唯一定位设备时优先提供"),
                                        property("deviceNumber", "string", "设备编号，用于精确定位设备"),
                                        property("deviceName", "string", "设备名称，仅在名称足够明确时提供"),
                                        property("categoryName", "string", "设备分类名称，可用于按分类查询可用设备")))));
        definitions.put(
                QUERY_MY_RESERVATIONS,
                new ToolDefinition(
                        QUERY_MY_RESERVATIONS,
                        "查询当前登录用户本人的预约列表或单条预约详情。",
                        schema(
                                properties(
                                        property("reservationId", "string", "预约编号；提供后优先查询单条预约详情"),
                                        integerProperty("page", "列表查询页码，从 1 开始"),
                                        integerProperty("size", "列表查询每页条数")))));
        definitions.put(
                CREATE_MY_RESERVATION,
                new ToolDefinition(
                        CREATE_MY_RESERVATION,
                        "为当前登录用户本人创建预约；必须提供设备定位信息、开始时间、结束时间和用途。",
                        schema(
                                properties(
                                        property("deviceId", "string", "设备 ID，已唯一定位时优先提供"),
                                        property("deviceNumber", "string", "设备编号，可用于定位设备"),
                                        property("deviceName", "string", "设备名称，可用于定位设备"),
                                        property("categoryName", "string", "设备分类名称，仅作为辅助筛选条件"),
                                        property("startTime", "string", "预约开始时间，必须是 ISO 本地时间，例如 2026-04-02T10:00:00"),
                                        property("endTime", "string", "预约结束时间，必须是 ISO 本地时间，例如 2026-04-02T12:00:00"),
                                        property("purpose", "string", "预约用途"),
                                        property("remark", "string", "预约备注，可为空")),
                                List.of("startTime", "endTime", "purpose"))));
        definitions.put(
                CANCEL_MY_RESERVATION,
                new ToolDefinition(
                        CANCEL_MY_RESERVATION,
                        "取消当前登录用户本人的预约；必须提供预约编号。",
                        schema(
                                properties(
                                        property("reservationId", "string", "预约编号"),
                                        property("reason", "string", "取消原因，可为空")),
                                List.of("reservationId"))));
        return Collections.unmodifiableMap(definitions);
    }

    private static ToolProperty property(String name, String type, String description) {
        return new ToolProperty(name, Map.of(
                "type", type,
                "description", description));
    }

    private static ToolProperty integerProperty(String name, String description) {
        return new ToolProperty(name, Map.of(
                "type", "integer",
                "description", description));
    }

    private static Map<String, Object> schema(Map<String, Object> properties) {
        return schema(properties, List.of());
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> properties(ToolProperty... properties) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ToolProperty property : properties) {
            values.put(property.name(), property.definition());
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 单个工具的正式声明。
     *
     * @param name 工具名
     * @param description 工具职责说明
     * @param parameters 函数参数 JSON Schema
     */
    public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

        public ToolDefinition {
            parameters = parameters == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }

    private record ToolProperty(String name, Map<String, Object> definition) {
    }
}
