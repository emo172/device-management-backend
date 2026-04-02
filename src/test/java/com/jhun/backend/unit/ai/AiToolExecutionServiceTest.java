package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.DeviceStatusLogResponse;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationListItemResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.service.DeviceService;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.service.support.ai.AiToolExecutionResult;
import com.jhun.backend.service.support.ai.AiToolExecutionService;
import com.jhun.backend.service.support.ai.AiToolRegistry;
import com.jhun.backend.service.support.ai.QwenExtractionSchema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AI 工具执行服务测试。
 * <p>
 * 该测试锁定 task 5 的核心边界：
 * 1) 只开放四个正式工具；
 * 2) 工具层只能通过 DeviceService / ReservationService 执行；
 * 3) 未知工具、缺参、歧义、跨用户和业务拒绝都必须返回可预测结果，而不是异常泄漏。
 */
class AiToolExecutionServiceTest {

    private static final String USER_ID = "user-1";

    private final DeviceService deviceService = mock(DeviceService.class);
    private final ReservationService reservationService = mock(ReservationService.class);
    private final AiToolRegistry aiToolRegistry = new AiToolRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiToolExecutionService aiToolExecutionService =
            new AiToolExecutionService(deviceService, reservationService, aiToolRegistry, objectMapper);

    /**
     * 验证工具白名单严格固定为四项，防止执行层与 provider 声明层各自维护不同列表。
     */
    @Test
    void shouldExposeOnlyFourSupportedTools() {
        assertThat(aiToolExecutionService.supportedToolNames()).containsExactly(
                AiToolRegistry.QUERY_DEVICE_AVAILABILITY,
                AiToolRegistry.QUERY_MY_RESERVATIONS,
                AiToolRegistry.CREATE_MY_RESERVATION,
                AiToolRegistry.CANCEL_MY_RESERVATION);
    }

    /**
     * 验证查询设备可用性会通过正式设备服务拿详情，并返回稳定序列化结果。
     */
    @Test
    void shouldQueryDeviceAvailabilityByResolvedDeviceId() throws Exception {
        when(deviceService.getDeviceDetail("dev-1"))
                .thenReturn(new DeviceDetailResponse(
                        "dev-1",
                        "投影仪 A",
                        "DEV-001",
                        "cat-1",
                        "教学设备",
                        "AVAILABLE",
                        "4K 投影",
                        "A101",
                        "/files/devices/dev-1.png",
                        List.of(new DeviceStatusLogResponse("MAINTENANCE", "AVAILABLE", "维修完成"))));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.QUERY_DEVICE_AVAILABILITY, Map.of(), "dev-1", null));

        verify(deviceService).getDeviceDetail("dev-1");
        verifyNoInteractions(reservationService);
        assertThat(result.success()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.payload()).containsKey("device");

        JsonNode json = objectMapper.readTree(result.serializedResult());
        assertThat(json.path("toolName").asText()).isEqualTo(AiToolRegistry.QUERY_DEVICE_AVAILABILITY);
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("payload").path("device").path("available").asBoolean()).isTrue();
        assertThat(json.path("payload").path("device").path("statusLogs").size()).isEqualTo(1);
    }

    /**
     * 验证查询本人预约列表只会走正式预约服务，并保留分页元信息给后续总结阶段使用。
     */
    @Test
    void shouldQueryMyReservationsThroughReservationService() throws Exception {
        when(reservationService.listReservations(USER_ID, "USER", 1, 10))
                .thenReturn(new ReservationPageResponse(
                        1,
                        List.of(new ReservationListItemResponse(
                                "res-1",
                                null,
                                USER_ID,
                                "alice",
                                USER_ID,
                                "alice",
                                "SELF",
                                "dev-1",
                                "投影仪 A",
                                "DEV-001",
                                LocalDateTime.of(2026, 4, 10, 9, 0),
                                LocalDateTime.of(2026, 4, 10, 10, 0),
                                "课程演示",
                                "APPROVED",
                                "NOT_CHECKED_IN",
                                "DEVICE_ONLY",
                                null,
                                null))));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.QUERY_MY_RESERVATIONS, Map.of(), null, null));

        verify(reservationService).listReservations(USER_ID, "USER", 1, 10);
        verifyNoInteractions(deviceService);
        assertThat(result.success()).isTrue();
        JsonNode json = objectMapper.readTree(result.serializedResult());
        assertThat(json.path("payload").path("total").asLong()).isEqualTo(1);
        assertThat(json.path("payload").path("count").asInt()).isEqualTo(1);
        assertThat(json.path("payload").path("reservations").get(0).path("status").asText()).isEqualTo("APPROVED");
    }

    /**
     * 验证创建本人预约时，工具层会先做设备唯一解析，再把结果转换成正式 DTO 调用预约服务。
     */
    @Test
    void shouldCreateReservationThroughReservationServiceOnly() {
        when(deviceService.listDevices(1, Integer.MAX_VALUE, null))
                .thenReturn(new DevicePageResponse(
                        1,
                        List.of(new DeviceResponse(
                                "dev-1",
                                "投影仪 A",
                                "DEV-001",
                                "cat-1",
                                "教学设备",
                                "AVAILABLE",
                                "4K 投影",
                                "A101",
                                "/files/devices/dev-1.png"))));
        when(reservationService.createReservation(eq(USER_ID), eq(USER_ID), any()))
                .thenReturn(new ReservationResponse(
                        "res-1",
                        null,
                        USER_ID,
                        "alice",
                        USER_ID,
                        "alice",
                        "SELF",
                        "dev-1",
                        "投影仪 A",
                        "DEV-001",
                        "AVAILABLE",
                        LocalDateTime.of(2026, 4, 10, 9, 0),
                        LocalDateTime.of(2026, 4, 10, 10, 0),
                        "课程演示",
                        "第一节课",
                        "PENDING_DEVICE_APPROVAL",
                        "NOT_CHECKED_IN",
                        "DEVICE_ONLY",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 2, 8, 0),
                        LocalDateTime.of(2026, 4, 2, 8, 0)));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(
                        AiToolRegistry.CREATE_MY_RESERVATION,
                        Map.of(
                                "deviceName", "投影仪 A",
                                "startTime", "2026-04-10T09:00:00",
                                "endTime", "2026-04-10T10:00:00",
                                "purpose", "课程演示",
                                "remark", "第一节课"),
                        null,
                        null));

        ArgumentCaptor<com.jhun.backend.dto.reservation.CreateReservationRequest> requestCaptor =
                ArgumentCaptor.forClass(com.jhun.backend.dto.reservation.CreateReservationRequest.class);
        verify(deviceService).listDevices(1, Integer.MAX_VALUE, null);
        verify(reservationService).createReservation(eq(USER_ID), eq(USER_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().deviceId()).isEqualTo("dev-1");
        assertThat(requestCaptor.getValue().purpose()).isEqualTo("课程演示");
        assertThat(requestCaptor.getValue().remark()).isEqualTo("第一节课");
        assertThat(result.success()).isTrue();
        assertThat(result.payload()).containsKey("reservation");
    }

    /**
     * 验证取消本人预约会走正式预约服务，并把取消后的详情稳定返回。
     */
    @Test
    void shouldCancelReservationThroughReservationServiceOnly() {
        when(reservationService.cancelReservation(eq("res-1"), eq(USER_ID), eq("USER"), any()))
                .thenReturn(new ReservationDetailResponse(
                        "res-1",
                        null,
                        USER_ID,
                        "alice",
                        USER_ID,
                        "alice",
                        "SELF",
                        "dev-1",
                        "投影仪 A",
                        "DEV-001",
                        "AVAILABLE",
                        LocalDateTime.of(2026, 4, 10, 9, 0),
                        LocalDateTime.of(2026, 4, 10, 10, 0),
                        "课程演示",
                        "第一节课",
                        "CANCELLED",
                        "NOT_CHECKED_IN",
                        "DEVICE_ONLY",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "临时改期",
                        LocalDateTime.of(2026, 4, 2, 9, 0),
                        null,
                        LocalDateTime.of(2026, 4, 2, 8, 0),
                        LocalDateTime.of(2026, 4, 2, 9, 0)));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.CANCEL_MY_RESERVATION, Map.of("reservationId", "res-1", "reason", "临时改期"), null, null));

        ArgumentCaptor<com.jhun.backend.dto.reservation.CancelReservationRequest> requestCaptor =
                ArgumentCaptor.forClass(com.jhun.backend.dto.reservation.CancelReservationRequest.class);
        verify(reservationService).cancelReservation(eq("res-1"), eq(USER_ID), eq("USER"), requestCaptor.capture());
        verifyNoInteractions(deviceService);
        assertThat(requestCaptor.getValue().reason()).isEqualTo("临时改期");
        assertThat(result.success()).isTrue();
        assertThat(result.payload()).containsKey("reservation");
    }

    /**
     * 验证未知工具不会抛出异常，而是返回带白名单的受控失败结果。
     */
    @Test
    void shouldRejectUnknownToolWithControlledFailure() {
        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction("invent_new_tool", Map.of(), null, null));

        verifyNoInteractions(deviceService, reservationService);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UNKNOWN_TOOL");
        assertThat(result.payload()).containsKey("supportedTools");
    }

    /**
     * 验证缺少 reservationId 时，不会把空参数直接下放到取消服务。
     */
    @Test
    void shouldRejectMissingReservationIdForCancelTool() {
        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.CANCEL_MY_RESERVATION, Map.of(), null, null));

        verifyNoInteractions(deviceService, reservationService);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(result.payload()).containsEntry("missingFields", List.of("reservationId"));
    }

    /**
     * 验证设备名称匹配出多条记录时，会在工具层显式返回歧义结果，避免把错误设备 ID 交给正式预约服务。
     */
    @Test
    void shouldRejectAmbiguousDeviceMatchWhenCreatingReservation() {
        when(deviceService.listDevices(1, Integer.MAX_VALUE, null))
                .thenReturn(new DevicePageResponse(
                        2,
                        List.of(
                                new DeviceResponse("dev-1", "投影仪", "DEV-001", "cat-1", "教学设备", "AVAILABLE", "A", "A101", null),
                                new DeviceResponse("dev-2", "投影仪", "DEV-002", "cat-1", "教学设备", "AVAILABLE", "B", "A102", null))));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(
                        AiToolRegistry.CREATE_MY_RESERVATION,
                        Map.of(
                                "deviceName", "投影仪",
                                "startTime", "2026-04-10T09:00:00",
                                "endTime", "2026-04-10T10:00:00",
                                "purpose", "课程演示"),
                        null,
                        null));

        verify(deviceService).listDevices(1, Integer.MAX_VALUE, null);
        verify(reservationService, never()).createReservation(eq(USER_ID), eq(USER_ID), any());
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AMBIGUOUS_RESOURCE");
        assertThat(result.payload()).containsKey("matches");
    }

    /**
     * 验证模型试图传入其他用户 ID 时，会在工具层前置拦截，保护“只处理本人数据”的边界。
     */
    @Test
    void shouldRejectCrossUserAccessBeforeCallingServices() {
        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.QUERY_MY_RESERVATIONS, Map.of("userId", "other-user"), null, null));

        verifyNoInteractions(deviceService, reservationService);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACCESS_DENIED");
    }

    /**
     * 验证取消窗口等业务拒绝会被稳定收口，而不是把 BusinessException 直接抛给上游 provider。
     */
    @Test
    void shouldReturnControlledBusinessRejectionWhenCancellationWindowIsInvalid() {
        when(reservationService.cancelReservation(eq("res-1"), eq(USER_ID), eq("USER"), any()))
                .thenThrow(new BusinessException("开始前 24 小时内取消需管理员处理"));

        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "USER",
                extraction(AiToolRegistry.CANCEL_MY_RESERVATION, Map.of("reservationId", "res-1"), null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("BUSINESS_REJECTED");
        assertThat(result.message()).isEqualTo("开始前 24 小时内取消需管理员处理");
    }

    /**
     * 验证 DEVICE_ADMIN / SYSTEM_ADMIN 不允许走 AI 工具执行路径，避免管理员能力通过自然语言入口渗透进来。
     */
    @Test
    void shouldRejectNonUserRoleBeforeExecutingAnyTool() {
        AiToolExecutionResult result = aiToolExecutionService.execute(
                USER_ID,
                "DEVICE_ADMIN",
                extraction(AiToolRegistry.QUERY_MY_RESERVATIONS, Map.of(), null, null));

        verifyNoInteractions(deviceService, reservationService);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(result.message()).isEqualTo("只有普通用户可以执行 AI 工具");
    }

    private QwenExtractionSchema.StructuredExtraction extraction(
            String toolName,
            Map<String, Object> toolArguments,
            String resolvedDeviceId,
            String resolvedReservationId) {
        return new QwenExtractionSchema.StructuredExtraction(
                "QUERY",
                BigDecimal.valueOf(0.95),
                toolName,
                toolArguments,
                List.of(),
                "test",
                resolvedDeviceId,
                resolvedReservationId);
    }
}
