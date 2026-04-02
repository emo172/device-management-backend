package com.jhun.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.common.enums.PromptTemplateType;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiChatCompletionResponse;
import com.jhun.backend.service.support.ai.qwen.QwenOpenAiClient;
import com.jhun.backend.util.UuidUtil;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 对话接口集成测试。
 * <p>
 * 本类专门把控制器层切到 `ai.provider=qwen`，但通过 Spring MockitoBean 替换真实 `QwenOpenAiClient`，
 * 以便在完全离线的测试环境里继续走真实的 `QwenAiProvider -> AiToolExecutionService -> Service` 编排链路，
 * 同时锁定 USER 专属入口、模板脏数据 fail-fast 和历史落库语义不会被 qwen 接入破坏。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 只有 AI 对话控制器集成测试需要显式开启聊天入口并切到 qwen，避免把条件装配影响扩散到其他 test profile。
        "ai.enabled=true",
        "ai.provider=qwen"
})
class AiControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private DeviceCategoryMapper deviceCategoryMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QwenOpenAiClient qwenOpenAiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        /*
         * `@MockitoBean` 会在整个 Spring 上下文里复用同一个替身 Bean。
         * 这里每次显式 reset，是为了避免前一个用例分配的 staged Qwen 响应串到后一个用例，影响集成测试的确定性。
         */
        reset(qwenOpenAiClient);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证 qwen 成功链路可以在无真实外网的前提下完成查询，并把执行状态、模型名和唯一资源主键稳定写入历史表。
     */
    @Test
    void shouldAllowUserToChatThroughQwenAndPersistSuccessHistory() throws Exception {
        User user = createUser("ai-qwen-user", "ai-qwen-user@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = insertReservation(
                user.getId(),
                device.getId(),
                alignedNow().plusDays(2).withHour(14).withMinute(0),
                alignedNow().plusDays(2).withHour(15).withMinute(0),
                "APPROVED");
        String sessionId = "123e4567-e89b-42d3-a456-426614174000";

        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "QUERY",
                                "query_my_reservations",
                                Map.of("reservationId", reservationId),
                                List.of(),
                                "可以查询预约详情。",
                                null,
                                reservationId),
                        stage2ToolCallResponse("call-query-1", "query_my_reservations", "{\"reservationId\":\"%s\"}".formatted(reservationId)),
                        finalFeedbackResponse("已查询到预约 %s，当前状态为 APPROVED。".formatted(reservationId)));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "message": "帮我查询预约 %s"
                                }
                                """.formatted(sessionId, reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.intent").value(AiIntentType.QUERY.name()))
                .andExpect(jsonPath("$.data.executeResult").value(AiExecuteResult.SUCCESS.name()))
                .andExpect(jsonPath("$.data.aiResponse").value("已查询到预约 %s，当前状态为 APPROVED。".formatted(reservationId)));

        AiHistoryRow history = loadHistoryBySessionId(user.getId(), sessionId);
        assertThat(history.intent()).isEqualTo(AiIntentType.QUERY.name());
        assertThat(history.executeResult()).isEqualTo(AiExecuteResult.SUCCESS.name());
        assertThat(history.llmModel()).isEqualTo("qwen-plus");
        assertThat(history.deviceId()).isEqualTo(device.getId());
        assertThat(history.reservationId()).isEqualTo(reservationId);
        assertThat(history.errorMessage()).isNull();
    }

    /**
     * 验证 qwen 第一阶段识别出缺失字段时，会直接返回 `PENDING` 并保留 qwen 模型留痕，而不是继续触发工具执行。
     */
    @Test
    void shouldPersistPendingHistoryWhenQwenNeedsMissingField() throws Exception {
        User user = createUser("ai-qwen-pending", "ai-qwen-pending@example.com", "USER");
        String sessionId = "223e4567-e89b-42d3-a456-426614174000";

        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(stage1ExtractionResponse(
                        "CANCEL",
                        "cancel_my_reservation",
                        Map.of(),
                        List.of("reservationId"),
                        "请先补充预约编号。",
                        null,
                        null));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "message": "帮我取消预约"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.intent").value(AiIntentType.CANCEL.name()))
                .andExpect(jsonPath("$.data.executeResult").value(AiExecuteResult.PENDING.name()))
                .andExpect(jsonPath("$.data.aiResponse").isNotEmpty());

        AiHistoryRow history = loadHistoryBySessionId(user.getId(), sessionId);
        assertThat(history.executeResult()).isEqualTo(AiExecuteResult.PENDING.name());
        assertThat(history.llmModel()).isEqualTo("qwen-plus");
        assertThat(history.deviceId()).isNull();
        assertThat(history.reservationId()).isNull();
        assertThat(history.errorMessage()).contains("请先补充预约编号");
    }

    /**
     * 验证 qwen 工具链命中正式业务拒绝时，会把业务错误收口成 `FAILED` 并写入历史，而不是悄悄吞成成功或 pending。
     */
    @Test
    void shouldPersistFailedHistoryWhenQwenToolHitsBusinessRejection() throws Exception {
        User user = createUser("ai-qwen-failed", "ai-qwen-failed@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = insertReservation(
                user.getId(),
                device.getId(),
                alignedNow().plusHours(6),
                alignedNow().plusHours(7),
                "APPROVED");
        String sessionId = "323e4567-e89b-42d3-a456-426614174000";

        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(
                        stage1ExtractionResponse(
                                "CANCEL",
                                "cancel_my_reservation",
                                Map.of("reservationId", reservationId),
                                List.of(),
                                "可以进入取消流程。",
                                null,
                                reservationId),
                        stage2ToolCallResponse("call-cancel-1", "cancel_my_reservation", "{\"reservationId\":\"%s\"}".formatted(reservationId)),
                        finalFeedbackResponse("当前预约距离开始不足 24 小时，请联系管理员处理。"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "message": "帮我取消预约 %s"
                                }
                                """.formatted(sessionId, reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.intent").value(AiIntentType.CANCEL.name()))
                .andExpect(jsonPath("$.data.executeResult").value(AiExecuteResult.FAILED.name()))
                .andExpect(jsonPath("$.data.aiResponse").value("当前预约距离开始不足 24 小时，请联系管理员处理。"));

        AiHistoryRow history = loadHistoryBySessionId(user.getId(), sessionId);
        assertThat(history.executeResult()).isEqualTo(AiExecuteResult.FAILED.name());
        assertThat(history.llmModel()).isEqualTo("qwen-plus");
        assertThat(history.deviceId()).isNull();
        assertThat(history.reservationId()).isEqualTo(reservationId);
        assertThat(history.errorMessage()).isEqualTo("开始前 24 小时内取消需管理员处理");
    }

    /**
     * 验证设备管理员不能使用 AI 对话接口，保护 USER 专属入口不会因 qwen provider 接入而放宽。
     */
    @Test
    void shouldRejectDeviceAdminUsingAiChat() throws Exception {
        User deviceAdmin = createUser("ai-device-admin", "ai-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "帮我查一下设备状态"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(qwenOpenAiClient);
    }

    /**
     * 验证超出会话 ID 长度上限的请求会被接口层直接拦截，避免非法值进入历史归并逻辑。
     */
    @Test
    void shouldRejectOverlongAiChatFieldAtControllerLayer() throws Exception {
        User user = createUser("ai-user-too-long", "ai-user-too-long@example.com", "USER");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "message": "查询一下我的预约历史"
                                }
                                """.formatted("s".repeat(37))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(qwenOpenAiClient);
    }

    /**
     * 验证会话 ID 不符合 UUID 格式时会在接口层被直接拦截，避免脏值进入会话归并逻辑。
     */
    @Test
    void shouldRejectInvalidSessionIdFormatAtControllerLayer() throws Exception {
        User user = createUser("ai-user-bad-uuid", "ai-user-bad-uuid@example.com", "USER");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "not-a-uuid",
                                  "message": "查询一下我的预约历史"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(qwenOpenAiClient);
    }

    /**
     * 验证当同一类型存在多条启用模板脏数据时，qwen 运行时也会继续保持 fail-fast，而不是吞成通用 AI 失败。
     */
    @Test
    void shouldFailFastWhenPromptTemplateTypeHasMultipleActiveRecords() throws Exception {
        User user = createUser("ai-user-dup", "ai-user-dup@example.com", "USER");
        insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK, "dup-result-1");
        insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK, "dup-result-2");

        when(qwenOpenAiClient.createChatCompletion(any()))
                .thenReturn(stage1ExtractionResponse(
                        "HELP",
                        null,
                        Map.of(),
                        List.of(),
                        "可以继续给出帮助说明。",
                        null,
                        null));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "我需要帮助"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("同一 Prompt 模板类型存在多条启用模板，请先清理脏数据"));
    }

    /**
     * 创建指定角色的测试用户。
     * <p>
     * 这里统一把用户状态设为可登录且未冻结，用于隔离“AI 角色边界”与“账号不可用”两类失败原因，
     * 确保 USER/qwen 路径和 DEVICE_ADMIN/403 断言保护的都是目标契约本身。
     */
    private User createUser(String username, String email, String roleName) {
        Role role = roleMapper.findByName(roleName);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138121");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 创建一台可供 AI 工具链查询的测试设备。
     * <p>
     * 这里沿用正式分类/设备表建数，而不是直接在 AI 历史里硬塞 deviceId，
     * 是为了让 qwen 成功链路真正经过 `ReservationService -> DeviceService` 的正式查询与资源回填逻辑。
     */
    private Device createDevice(String approvalMode) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("AI 分类-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 6));
        category.setSortOrder(1);
        category.setDescription("AI 集成测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("AI 设备-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 6));
        device.setDeviceNumber("AI-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("AI 集成测试设备");
        device.setLocation("Lab-AI");
        deviceMapper.insert(device);
        return device;
    }

    /**
     * 直接插入一条正式预约记录。
     * <p>
     * 该辅助方法只负责为 AI 集成测试准备最小充分的业务事实数据，让查询/取消工具能够命中真实服务规则；
     * 其中开始时间由各用例显式控制，用来区分“可查询成功”和“24 小时内取消被拒绝”两类不同业务语义。
     */
    private String insertReservation(String userId, String deviceId, LocalDateTime startTime, LocalDateTime endTime, String status) {
        String reservationId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                """
                        INSERT INTO reservation (
                            id, user_id, created_by, reservation_mode, device_id,
                            start_time, end_time, purpose, status, approval_mode_snapshot,
                            remark, sign_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                reservationId,
                userId,
                userId,
                "SELF",
                deviceId,
                Timestamp.valueOf(startTime.truncatedTo(ChronoUnit.SECONDS)),
                Timestamp.valueOf(endTime.truncatedTo(ChronoUnit.SECONDS)),
                "AI 集成测试预约",
                status,
                "DEVICE_ONLY",
                "AI 集成测试备注",
                "NOT_CHECKED_IN");
        return reservationId;
    }

    /**
     * 读取指定会话刚落下的一条 AI 历史记录。
     * <p>
     * 这里显式校验 `execute_result/error_message/llm_model/device_id/reservation_id`，
     * 是为了确保控制器测试不仅看到 HTTP 返回，还真正保护了 qwen 路径的历史留痕语义。
     */
    private AiHistoryRow loadHistoryBySessionId(String userId, String sessionId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT intent, execute_result, error_message, llm_model, device_id, reservation_id
                        FROM chat_history
                        WHERE user_id = ? AND session_id = ?
                        """,
                (resultSet, rowNum) -> new AiHistoryRow(
                        resultSet.getString("intent"),
                        resultSet.getString("execute_result"),
                        resultSet.getString("error_message"),
                        resultSet.getString("llm_model"),
                        resultSet.getString("device_id"),
                        resultSet.getString("reservation_id")),
                userId,
                sessionId);
    }

    /**
     * 为测试用户生成 Bearer Token。
     * <p>
     * Token 中的角色声明会直接影响安全链路和方法鉴权结果，因此这里由调用方显式传入角色，
     * 避免测试误把数据库角色和 JWT 角色混为一谈而掩盖权限问题。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    /**
     * 插入启用中的 Prompt 模板脏数据，专门用于验证 qwen 路径不会静默选中任意一条模板继续执行。
     */
    private void insertActivePromptTemplate(PromptTemplateType type, String suffix) {
        jdbcTemplate.update(
                """
                        INSERT INTO prompt_template (
                            id, name, code, content, type, description, variables, is_active, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UuidUtil.randomUuid(),
                type.name() + "-" + suffix,
                type.name() + "_" + suffix,
                "脏数据模板",
                type.name(),
                "测试运行时冲突",
                "[\"message\"]",
                1,
                "1.0");
    }

    private LocalDateTime alignedNow() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    private QwenOpenAiChatCompletionResponse stage1ExtractionResponse(
            String intent,
            String toolName,
            Map<String, Object> toolArguments,
            List<String> missingFields,
            String replyHint,
            String resolvedDeviceId,
            String resolvedReservationId) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", intent);
        payload.put("intentConfidence", 0.95);
        payload.put("toolName", toolName);
        payload.put("toolArguments", toolArguments);
        payload.put("missingFields", missingFields);
        payload.put("replyHint", replyHint);
        payload.put("resolvedDeviceId", resolvedDeviceId);
        payload.put("resolvedReservationId", resolvedReservationId);
        return new QwenOpenAiChatCompletionResponse(
                "stage-1",
                "qwen-plus",
                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                        0,
                        new QwenOpenAiChatCompletionResponse.Message(
                                "assistant",
                                objectMapper.writeValueAsString(payload),
                                List.of()),
                        "stop")),
                null);
    }

    private QwenOpenAiChatCompletionResponse stage2ToolCallResponse(String callId, String toolName, String argumentsJson) {
        return new QwenOpenAiChatCompletionResponse(
                "stage-2",
                "qwen-plus",
                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                        0,
                        new QwenOpenAiChatCompletionResponse.Message(
                                "assistant",
                                null,
                                List.of(new QwenOpenAiChatCompletionResponse.ToolCall(
                                        callId,
                                        "function",
                                        new QwenOpenAiChatCompletionResponse.FunctionCall(toolName, argumentsJson)))),
                        "tool_calls")),
                null);
    }

    private QwenOpenAiChatCompletionResponse finalFeedbackResponse(String content) {
        return new QwenOpenAiChatCompletionResponse(
                "stage-3",
                "qwen-plus",
                List.of(new QwenOpenAiChatCompletionResponse.Choice(
                        0,
                        new QwenOpenAiChatCompletionResponse.Message("assistant", content, List.of()),
                        "stop")),
                null);
    }

    private record AiHistoryRow(
            String intent,
            String executeResult,
            String errorMessage,
            String llmModel,
            String deviceId,
            String reservationId) {
    }
}
