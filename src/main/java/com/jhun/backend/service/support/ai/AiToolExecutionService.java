package com.jhun.backend.service.support.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.DeviceStatusLogResponse;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationListItemResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.service.DeviceService;
import com.jhun.backend.service.ReservationService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 工具执行服务。
 * <p>
 * 该服务是“模型工具调用”与“正式业务 Service 边界”之间的唯一胶水层：
 * 它负责把结构化提取阶段输出的 toolName / toolArguments 转换为现有 DTO，
 * 再严格通过 {@link DeviceService} 与 {@link ReservationService} 执行业务动作。
 * <p>
 * 这里显式承担三类防线：
 * 1) 只允许四个正式工具，杜绝模型幻觉工具名；
 * 2) 只允许 USER 语义，杜绝 AI 入口越权走到管理员能力；
 * 3) 所有失败都收敛成受控结果对象，禁止把底层异常栈直接暴露给上游 provider 或最终用户。
 */
@Service
public class AiToolExecutionService {

    private static final String USER_ROLE = "USER";
    private static final int DEFAULT_RESERVATION_PAGE = 1;
    private static final int DEFAULT_RESERVATION_SIZE = 10;
    /**
     * 设备服务当前没有“按名称 / 编号查单条”接口，AI 层只能通过正式分页接口拉取候选再做受控匹配。
     * 这里固定第一页 + 最大 size，是为了保持“只走 DeviceService 边界”这条红线，而不是回退到 Mapper 直查。
     */
    private static final int DEVICE_SEARCH_PAGE_SIZE = Integer.MAX_VALUE;
    private static final DateTimeFormatter TOOL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final List<String> USER_BOUNDARY_ARGUMENT_KEYS = List.of("userId", "targetUserId", "createdBy", "operatorId");

    private final DeviceService deviceService;
    private final ReservationService reservationService;
    private final AiToolRegistry aiToolRegistry;
    private final ObjectMapper objectMapper;

    public AiToolExecutionService(
            DeviceService deviceService,
            ReservationService reservationService,
            AiToolRegistry aiToolRegistry,
            ObjectMapper objectMapper) {
        this.deviceService = deviceService;
        this.reservationService = reservationService;
        this.aiToolRegistry = aiToolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 返回当前正式开放的工具列表。
     * <p>
     * 后续 provider 只能基于这份列表生成 tool definition，避免执行层与声明层出现口径漂移。
     */
    public List<String> supportedToolNames() {
        return aiToolRegistry.supportedToolNames();
    }

    /**
     * 执行一条结构化提取后的 AI 工具请求。
     * <p>
     * 这里直接消费 task 4 产出的结构化真相源，避免再复制一套平行字段模型；
     * 同时保留 `resolvedDeviceId` / `resolvedReservationId`，让上游已经唯一解析出的资源 ID 可以在只读查询和历史留痕里复用；
     * 但对创建、取消这类会触发真实写操作的工具，执行层仍必须坚持“正式工具参数或服务侧唯一解析结果才算权威主键”，
     * 不能把模型推测出的资源 ID 直接当成写链路入参。
     */
    public AiToolExecutionResult execute(String userId, String role, QwenExtractionSchema.StructuredExtraction extraction) {
        if (extraction == null) {
            return failure(
                    null,
                    "INVALID_ARGUMENT",
                    "结构化提取结果不能为空",
                    Map.of("missingFields", List.of("toolName", "toolArguments")));
        }
        return execute(userId, role, extraction.toolName(), extraction.toolArguments(), extraction.resolvedDeviceId(), extraction.resolvedReservationId());
    }

    /**
     * 执行一条 AI 工具请求。
     * <p>
     * 对外暴露显式参数形式，是为了方便后续 provider 工作流与单元测试直接复用；
     * 但无论入口来自哪里，最终都统一走同一套角色、白名单、缺参和异常收口逻辑。
     */
    public AiToolExecutionResult execute(
            String userId,
            String role,
            String toolName,
            Map<String, Object> toolArguments,
            String resolvedDeviceId,
            String resolvedReservationId) {
        String normalizedToolName = trimToNull(toolName);
        Map<String, Object> normalizedArguments = copyArguments(toolArguments);
        try {
            ensureUserRole(role, normalizedToolName);
            ensureKnownTool(normalizedToolName);
            ensureSameUserBoundary(userId, normalizedToolName, normalizedArguments);
            return switch (normalizedToolName) {
                case AiToolRegistry.QUERY_DEVICE_AVAILABILITY ->
                        executeQueryDeviceAvailability(normalizedToolName, normalizedArguments, resolvedDeviceId);
                case AiToolRegistry.QUERY_MY_RESERVATIONS ->
                        executeQueryMyReservations(userId, normalizedToolName, normalizedArguments, resolvedReservationId);
                case AiToolRegistry.CREATE_MY_RESERVATION ->
                        executeCreateMyReservation(userId, normalizedToolName, normalizedArguments);
                case AiToolRegistry.CANCEL_MY_RESERVATION ->
                        executeCancelMyReservation(userId, normalizedToolName, normalizedArguments);
                default -> throw controlledFailure(
                        normalizedToolName,
                        "UNKNOWN_TOOL",
                        "当前工具未在 AI 白名单中开放",
                        Map.of("supportedTools", aiToolRegistry.supportedToolNames()));
            };
        } catch (ControlledToolFailure failure) {
            return failure(failure.toolName(), failure.errorCode(), failure.getMessage(), failure.payload());
        } catch (BusinessException businessException) {
            return failure(
                    normalizedToolName,
                    classifyBusinessErrorCode(businessException.getMessage()),
                    businessException.getMessage(),
                    Map.of());
        } catch (Exception exception) {
            return failure(
                    normalizedToolName,
                    "INTERNAL_ERROR",
                    "AI 工具执行失败，请稍后重试",
                    Map.of());
        }
    }

    private AiToolExecutionResult executeQueryDeviceAvailability(
            String toolName,
            Map<String, Object> toolArguments,
            String resolvedDeviceId) {
        String categoryName = readOptionalText(toolArguments, "categoryName", toolName);
        String deviceId = firstNonBlank(resolvedDeviceId, readOptionalText(toolArguments, "deviceId", toolName));
        if (deviceId != null) {
            DeviceDetailResponse device = deviceService.getDeviceDetail(deviceId);
            return success(toolName, "已查询设备可用性", Map.of("device", toDeviceDetailPayload(device)));
        }

        String deviceNumber = readOptionalText(toolArguments, "deviceNumber", toolName);
        String deviceName = readOptionalText(toolArguments, "deviceName", toolName);
        if (deviceNumber == null && deviceName == null && categoryName == null) {
            throw controlledFailure(
                    toolName,
                    "INVALID_ARGUMENT",
                    "查询设备可用性时至少需要提供 deviceId、deviceNumber、deviceName 或 categoryName 之一",
                    Map.of("missingFields", List.of("deviceId/deviceNumber/deviceName/categoryName")));
        }

        DevicePageResponse pageResponse = deviceService.listDevices(DEFAULT_RESERVATION_PAGE, DEVICE_SEARCH_PAGE_SIZE, categoryName);
        if (deviceNumber == null && deviceName == null) {
            List<Map<String, Object>> availableDevices = pageResponse.records().stream()
                    .filter(device -> isAvailableStatus(device.status()))
                    .map(this::toDeviceSummaryPayload)
                    .toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("categoryName", categoryName);
            payload.put("availableCount", availableDevices.size());
            payload.put("devices", availableDevices);
            String message = availableDevices.isEmpty() ? "当前条件下没有可用设备" : "已查询当前可用设备";
            return success(toolName, message, payload);
        }

        List<DeviceResponse> matches = pageResponse.records().stream()
                .filter(device -> matchesDevice(device, deviceNumber, deviceName))
                .toList();
        if (matches.isEmpty()) {
            throw controlledFailure(
                    toolName,
                    "RESOURCE_NOT_FOUND",
                    "未找到匹配的设备",
                    buildDeviceLookupPayload(categoryName, deviceNumber, deviceName, List.of()));
        }
        if (matches.size() > 1) {
            throw controlledFailure(
                    toolName,
                    "AMBIGUOUS_RESOURCE",
                    "匹配到多台设备，请补充更精确的设备编号或设备 ID",
                    buildDeviceLookupPayload(categoryName, deviceNumber, deviceName, matches.stream()
                            .map(this::toDeviceSummaryPayload)
                            .toList()));
        }

        DeviceDetailResponse device = deviceService.getDeviceDetail(matches.getFirst().id());
        return success(toolName, "已查询设备可用性", Map.of("device", toDeviceDetailPayload(device)));
    }

    private AiToolExecutionResult executeQueryMyReservations(
            String userId,
            String toolName,
            Map<String, Object> toolArguments,
            String resolvedReservationId) {
        String reservationId = firstNonBlank(resolvedReservationId, readOptionalText(toolArguments, "reservationId", toolName));
        if (reservationId != null) {
            ReservationDetailResponse reservation = reservationService.getReservationDetail(reservationId, userId, USER_ROLE);
            return success(toolName, "已查询本人预约详情", Map.of("reservation", toReservationDetailPayload(reservation)));
        }

        int page = readOptionalPositiveInteger(toolArguments, "page", toolName, DEFAULT_RESERVATION_PAGE);
        int size = readOptionalPositiveInteger(toolArguments, "size", toolName, DEFAULT_RESERVATION_SIZE);
        ReservationPageResponse reservationPage = reservationService.listReservations(userId, USER_ROLE, page, size);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("size", size);
        payload.put("total", reservationPage.total());
        payload.put("count", reservationPage.records().size());
        payload.put("reservations", reservationPage.records().stream().map(this::toReservationListPayload).toList());
        return success(toolName, "已查询本人预约列表", payload);
    }

    private AiToolExecutionResult executeCreateMyReservation(
            String userId,
            String toolName,
            Map<String, Object> toolArguments) {
        String deviceId = resolveDeviceId(toolName, toolArguments);
        LocalDateTime startTime = readRequiredDateTime(toolArguments, "startTime", toolName);
        LocalDateTime endTime = readRequiredDateTime(toolArguments, "endTime", toolName);
        String purpose = readRequiredText(toolArguments, "purpose", toolName);
        String remark = readOptionalText(toolArguments, "remark", toolName);

        ReservationResponse reservation = reservationService.createReservation(
                userId,
                userId,
                new CreateReservationRequest(deviceId, startTime, endTime, purpose, remark));
        return success(toolName, "已创建本人预约", Map.of("reservation", toReservationWorkflowPayload(reservation)));
    }

    private AiToolExecutionResult executeCancelMyReservation(
            String userId,
            String toolName,
            Map<String, Object> toolArguments) {
        String reservationId = readOptionalText(toolArguments, "reservationId", toolName);
        if (reservationId == null) {
            throw controlledFailure(
                    toolName,
                    "INVALID_ARGUMENT",
                    "取消预约时必须提供 reservationId",
                    Map.of("missingFields", List.of("reservationId")));
        }
        String reason = readOptionalText(toolArguments, "reason", toolName);
        ReservationDetailResponse reservation = reservationService.cancelReservation(
                reservationId,
                userId,
                USER_ROLE,
                new CancelReservationRequest(reason));
        return success(toolName, "已取消本人预约", Map.of("reservation", toReservationDetailPayload(reservation)));
    }

    /**
     * AI 工具层必须自己做“名称/编号 -> 唯一设备 ID”裁决。
     * <p>
     * 原因不是要复制设备业务，而是当前正式服务边界没有暴露“按名称查单条设备”能力；
     * 因此这里只能走列表接口拿候选，再在工具层把“唯一命中 / 歧义 / 未命中”三种结果显式化，避免 provider 直接猜测设备 ID。
     */
    private String resolveDeviceId(String toolName, Map<String, Object> toolArguments) {
        String deviceId = readOptionalText(toolArguments, "deviceId", toolName);
        if (deviceId != null) {
            return deviceId;
        }

        String deviceNumber = readOptionalText(toolArguments, "deviceNumber", toolName);
        String deviceName = readOptionalText(toolArguments, "deviceName", toolName);
        String categoryName = readOptionalText(toolArguments, "categoryName", toolName);
        if (deviceNumber == null && deviceName == null) {
            throw controlledFailure(
                    toolName,
                    "INVALID_ARGUMENT",
                    "创建预约时必须提供可唯一定位设备的 deviceId、deviceNumber 或 deviceName",
                    Map.of("missingFields", List.of("deviceId/deviceNumber/deviceName")));
        }

        DevicePageResponse pageResponse = deviceService.listDevices(DEFAULT_RESERVATION_PAGE, DEVICE_SEARCH_PAGE_SIZE, categoryName);
        List<DeviceResponse> matches = pageResponse.records().stream()
                .filter(device -> matchesDevice(device, deviceNumber, deviceName))
                .toList();
        if (matches.isEmpty()) {
            throw controlledFailure(
                    toolName,
                    "RESOURCE_NOT_FOUND",
                    "未找到可用于创建预约的设备",
                    buildDeviceLookupPayload(categoryName, deviceNumber, deviceName, List.of()));
        }
        if (matches.size() > 1) {
            throw controlledFailure(
                    toolName,
                    "AMBIGUOUS_RESOURCE",
                    "设备名称或编号匹配到多条记录，请补充更精确的设备信息",
                    buildDeviceLookupPayload(categoryName, deviceNumber, deviceName, matches.stream()
                            .map(this::toDeviceSummaryPayload)
                            .toList()));
        }
        return matches.getFirst().id();
    }

    private void ensureUserRole(String role, String toolName) {
        /*
         * AI 工具层必须把“只有 USER 可以执行”这条边界前置拦住，
         * 不能等到底层 Service 再靠不同错误文案兜底，否则 provider 视角会混入管理员能力幻觉。
         */
        if (!USER_ROLE.equals(role)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", role);
            throw controlledFailure(toolName, "ACCESS_DENIED", "只有普通用户可以执行 AI 工具", payload);
        }
    }

    private void ensureKnownTool(String toolName) {
        if (toolName == null) {
            throw controlledFailure(
                    null,
                    "UNKNOWN_TOOL",
                    "当前未识别到可执行工具",
                    Map.of("supportedTools", aiToolRegistry.supportedToolNames()));
        }
        if (!aiToolRegistry.supports(toolName)) {
            throw controlledFailure(
                    toolName,
                    "UNKNOWN_TOOL",
                    "当前工具未在 AI 白名单中开放",
                    Map.of("supportedTools", aiToolRegistry.supportedToolNames()));
        }
    }

    private void ensureSameUserBoundary(String userId, String toolName, Map<String, Object> toolArguments) {
        for (String key : USER_BOUNDARY_ARGUMENT_KEYS) {
            Object rawValue = toolArguments.get(key);
            if (rawValue == null) {
                continue;
            }
            if (!(rawValue instanceof String textValue)) {
                throw controlledFailure(toolName, "INVALID_ARGUMENT", "字段 %s 必须是字符串".formatted(key), Map.of("field", key));
            }
            String providedUserId = trimToNull(textValue);
            if (providedUserId != null && !providedUserId.equals(userId)) {
                /*
                 * 这里显式拒绝模型在参数里塞入其他用户 ID，
                 * 防止 Qwen 通过“帮别人查一下”之类的话术把 AI 工具误导成代查询或代预约入口。
                 */
                throw controlledFailure(toolName, "ACCESS_DENIED", "AI 工具只能处理当前登录用户本人数据", Map.of("argument", key));
            }
        }
    }

    private ControlledToolFailure controlledFailure(String toolName, String errorCode, String message, Map<String, Object> payload) {
        return new ControlledToolFailure(toolName, errorCode, message, payload);
    }

    private AiToolExecutionResult success(String toolName, String message, Map<String, Object> payload) {
        return buildResult(toolName, true, null, message, payload);
    }

    private AiToolExecutionResult failure(String toolName, String errorCode, String message, Map<String, Object> payload) {
        return buildResult(toolName, false, errorCode, message, payload);
    }

    private AiToolExecutionResult buildResult(
            String toolName,
            boolean success,
            String errorCode,
            String message,
            Map<String, Object> payload) {
        Map<String, Object> normalizedPayload = copyArguments(payload);
        Map<String, Object> serializedView = new LinkedHashMap<>();
        serializedView.put("toolName", toolName);
        serializedView.put("success", success);
        serializedView.put("errorCode", errorCode);
        serializedView.put("message", message);
        serializedView.put("payload", normalizedPayload);
        return new AiToolExecutionResult(toolName, success, errorCode, message, normalizedPayload, serialize(serializedView));
    }

    private String serialize(Map<String, Object> serializedView) {
        try {
            return objectMapper.writeValueAsString(serializedView);
        } catch (JsonProcessingException exception) {
            /*
             * 工具结果序列化本身也不能把底层异常抛回上游。
             * 一旦出现意外序列化失败，这里退回固定字段子集，至少保证 provider 后续还能拿到稳定 JSON 外壳继续总结。
             */
            try {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("toolName", serializedView.get("toolName"));
                fallback.put("success", serializedView.get("success"));
                fallback.put("errorCode", "INTERNAL_ERROR");
                fallback.put("message", "AI 工具结果序列化失败，已返回精简结果");
                fallback.put("payload", Map.of());
                return objectMapper.writeValueAsString(fallback);
            } catch (JsonProcessingException ignored) {
                return "{\"success\":false,\"errorCode\":\"INTERNAL_ERROR\",\"message\":\"AI 工具结果序列化失败\",\"payload\":{}}";
            }
        }
    }

    private String classifyBusinessErrorCode(String message) {
        if (message == null || message.isBlank()) {
            return "BUSINESS_REJECTED";
        }
        if (message.contains("只能") || message.contains("只有") || message.contains("当前角色不允许")) {
            return "ACCESS_DENIED";
        }
        if (message.contains("不存在")) {
            return "RESOURCE_NOT_FOUND";
        }
        return "BUSINESS_REJECTED";
    }

    private Map<String, Object> buildDeviceLookupPayload(
            String categoryName,
            String deviceNumber,
            String deviceName,
            List<Map<String, Object>> matches) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryName", categoryName);
        payload.put("deviceNumber", deviceNumber);
        payload.put("deviceName", deviceName);
        payload.put("matches", matches);
        return payload;
    }

    private boolean matchesDevice(DeviceResponse device, String deviceNumber, String deviceName) {
        boolean numberMatches = deviceNumber == null || equalsIgnoreCase(deviceNumber, device.deviceNumber());
        boolean nameMatches = deviceName == null || equalsIgnoreCase(deviceName, device.name());
        return numberMatches && nameMatches;
    }

    private boolean equalsIgnoreCase(String expected, String actual) {
        return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean isAvailableStatus(String status) {
        return "AVAILABLE".equalsIgnoreCase(status == null ? null : status.trim());
    }

    private int readOptionalPositiveInteger(
            Map<String, Object> toolArguments,
            String fieldName,
            String toolName,
            int defaultValue) {
        Object value = toolArguments.get(fieldName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed <= 0) {
                throw controlledFailure(toolName, "INVALID_ARGUMENT", "字段 %s 必须大于 0".formatted(fieldName), Map.of("field", fieldName));
            }
            return parsed;
        }
        if (value instanceof String text) {
            String normalized = trimToNull(text);
            if (normalized == null) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(normalized);
                if (parsed <= 0) {
                    throw controlledFailure(toolName, "INVALID_ARGUMENT", "字段 %s 必须大于 0".formatted(fieldName), Map.of("field", fieldName));
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw controlledFailure(toolName, "INVALID_ARGUMENT", "字段 %s 必须是整数".formatted(fieldName), Map.of("field", fieldName));
            }
        }
        throw controlledFailure(toolName, "INVALID_ARGUMENT", "字段 %s 必须是整数".formatted(fieldName), Map.of("field", fieldName));
    }

    private LocalDateTime readRequiredDateTime(Map<String, Object> toolArguments, String fieldName, String toolName) {
        String value = readRequiredText(toolArguments, fieldName, toolName);
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw controlledFailure(
                    toolName,
                    "INVALID_ARGUMENT",
                    "字段 %s 必须是 ISO 本地时间，例如 2026-04-02T10:00:00".formatted(fieldName),
                    Map.of("field", fieldName));
        }
    }

    private String readRequiredText(Map<String, Object> toolArguments, String fieldName, String toolName) {
        String value = readOptionalText(toolArguments, fieldName, toolName);
        if (value == null) {
            throw controlledFailure(
                    toolName,
                    "INVALID_ARGUMENT",
                    "字段 %s 不能为空".formatted(fieldName),
                    Map.of("missingFields", List.of(fieldName)));
        }
        return value;
    }

    private String readOptionalText(Map<String, Object> toolArguments, String fieldName, String toolName) {
        return readOptionalText(toolArguments, fieldName, toolName, true);
    }

    private String readOptionalText(
            Map<String, Object> toolArguments,
            String fieldName,
            String toolName,
            boolean failWhenWrongType) {
        Object value = toolArguments.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return trimToNull(text);
        }
        if (!failWhenWrongType) {
            return null;
        }
        throw controlledFailure(
                toolName,
                "INVALID_ARGUMENT",
                "字段 %s 必须是字符串".formatted(fieldName),
                Map.of("field", fieldName));
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String normalized = trimToNull(candidate);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> copyArguments(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private Map<String, Object> toDeviceSummaryPayload(DeviceResponse device) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", device.id());
        payload.put("name", device.name());
        payload.put("deviceNumber", device.deviceNumber());
        payload.put("categoryId", device.categoryId());
        payload.put("categoryName", device.categoryName());
        payload.put("status", device.status());
        payload.put("available", isAvailableStatus(device.status()));
        payload.put("description", device.description());
        payload.put("location", device.location());
        payload.put("imageUrl", device.imageUrl());
        return payload;
    }

    private Map<String, Object> toDeviceDetailPayload(DeviceDetailResponse device) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", device.id());
        payload.put("name", device.name());
        payload.put("deviceNumber", device.deviceNumber());
        payload.put("categoryId", device.categoryId());
        payload.put("categoryName", device.categoryName());
        payload.put("status", device.status());
        payload.put("available", isAvailableStatus(device.status()));
        payload.put("description", device.description());
        payload.put("location", device.location());
        payload.put("imageUrl", device.imageUrl());
        payload.put("statusLogs", device.statusLogs().stream().map(this::toDeviceStatusLogPayload).toList());
        return payload;
    }

    private Map<String, Object> toDeviceStatusLogPayload(DeviceStatusLogResponse statusLog) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("oldStatus", statusLog.oldStatus());
        payload.put("newStatus", statusLog.newStatus());
        payload.put("reason", statusLog.reason());
        return payload;
    }

    private Map<String, Object> toReservationListPayload(ReservationListItemResponse reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", reservation.id());
        payload.put("batchId", reservation.batchId());
        payload.put("userId", reservation.userId());
        payload.put("userName", reservation.userName());
        payload.put("createdBy", reservation.createdBy());
        payload.put("createdByName", reservation.createdByName());
        payload.put("reservationMode", reservation.reservationMode());
        payload.put("deviceId", reservation.deviceId());
        payload.put("deviceName", reservation.deviceName());
        payload.put("deviceNumber", reservation.deviceNumber());
        payload.put("startTime", formatTime(reservation.startTime()));
        payload.put("endTime", formatTime(reservation.endTime()));
        payload.put("purpose", reservation.purpose());
        payload.put("status", reservation.status());
        payload.put("signStatus", reservation.signStatus());
        payload.put("approvalModeSnapshot", reservation.approvalModeSnapshot());
        payload.put("cancelReason", reservation.cancelReason());
        payload.put("cancelTime", formatTime(reservation.cancelTime()));
        return payload;
    }

    private Map<String, Object> toReservationDetailPayload(ReservationDetailResponse reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", reservation.id());
        payload.put("batchId", reservation.batchId());
        payload.put("userId", reservation.userId());
        payload.put("userName", reservation.userName());
        payload.put("createdBy", reservation.createdBy());
        payload.put("createdByName", reservation.createdByName());
        payload.put("reservationMode", reservation.reservationMode());
        payload.put("deviceId", reservation.deviceId());
        payload.put("deviceName", reservation.deviceName());
        payload.put("deviceNumber", reservation.deviceNumber());
        payload.put("deviceStatus", reservation.deviceStatus());
        payload.put("startTime", formatTime(reservation.startTime()));
        payload.put("endTime", formatTime(reservation.endTime()));
        payload.put("purpose", reservation.purpose());
        payload.put("remark", reservation.remark());
        payload.put("status", reservation.status());
        payload.put("signStatus", reservation.signStatus());
        payload.put("approvalModeSnapshot", reservation.approvalModeSnapshot());
        payload.put("deviceApproverId", reservation.deviceApproverId());
        payload.put("deviceApproverName", reservation.deviceApproverName());
        payload.put("deviceApprovedAt", formatTime(reservation.deviceApprovedAt()));
        payload.put("deviceApprovalRemark", reservation.deviceApprovalRemark());
        payload.put("systemApproverId", reservation.systemApproverId());
        payload.put("systemApproverName", reservation.systemApproverName());
        payload.put("systemApprovedAt", formatTime(reservation.systemApprovedAt()));
        payload.put("systemApprovalRemark", reservation.systemApprovalRemark());
        payload.put("cancelReason", reservation.cancelReason());
        payload.put("cancelTime", formatTime(reservation.cancelTime()));
        payload.put("checkedInAt", formatTime(reservation.checkedInAt()));
        payload.put("createdAt", formatTime(reservation.createdAt()));
        payload.put("updatedAt", formatTime(reservation.updatedAt()));
        return payload;
    }

    private Map<String, Object> toReservationWorkflowPayload(ReservationResponse reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", reservation.id());
        payload.put("batchId", reservation.batchId());
        payload.put("userId", reservation.userId());
        payload.put("userName", reservation.userName());
        payload.put("createdBy", reservation.createdBy());
        payload.put("createdByName", reservation.createdByName());
        payload.put("reservationMode", reservation.reservationMode());
        payload.put("deviceId", reservation.deviceId());
        payload.put("deviceName", reservation.deviceName());
        payload.put("deviceNumber", reservation.deviceNumber());
        payload.put("deviceStatus", reservation.deviceStatus());
        payload.put("startTime", formatTime(reservation.startTime()));
        payload.put("endTime", formatTime(reservation.endTime()));
        payload.put("purpose", reservation.purpose());
        payload.put("remark", reservation.remark());
        payload.put("status", reservation.status());
        payload.put("signStatus", reservation.signStatus());
        payload.put("approvalModeSnapshot", reservation.approvalModeSnapshot());
        payload.put("deviceApproverId", reservation.deviceApproverId());
        payload.put("deviceApproverName", reservation.deviceApproverName());
        payload.put("deviceApprovedAt", formatTime(reservation.deviceApprovedAt()));
        payload.put("deviceApprovalRemark", reservation.deviceApprovalRemark());
        payload.put("systemApproverId", reservation.systemApproverId());
        payload.put("systemApproverName", reservation.systemApproverName());
        payload.put("systemApprovedAt", formatTime(reservation.systemApprovedAt()));
        payload.put("systemApprovalRemark", reservation.systemApprovalRemark());
        payload.put("cancelReason", reservation.cancelReason());
        payload.put("cancelTime", formatTime(reservation.cancelTime()));
        payload.put("checkedInAt", formatTime(reservation.checkedInAt()));
        payload.put("createdAt", formatTime(reservation.createdAt()));
        payload.put("updatedAt", formatTime(reservation.updatedAt()));
        return payload;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TOOL_TIME_FORMATTER.format(time);
    }

    /**
     * 内部受控失败。
     * <p>
     * 该异常仅在当前服务内部流转，用于把“缺参 / 越权 / 歧义”这类预期失败从流程控制中抽出来；
     * 对外仍然统一收敛成 {@link AiToolExecutionResult}，不会把异常本体暴露给上游。
     */
    private static final class ControlledToolFailure extends RuntimeException {

        private final String toolName;
        private final String errorCode;
        private final Map<String, Object> payload;

        private ControlledToolFailure(String toolName, String errorCode, String message, Map<String, Object> payload) {
            super(message);
            this.toolName = toolName;
            this.errorCode = errorCode;
            this.payload = payload == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }

        private String toolName() {
            return toolName;
        }

        private String errorCode() {
            return errorCode;
        }

        private Map<String, Object> payload() {
            return payload;
        }
    }
}
