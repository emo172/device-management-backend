package com.jhun.backend.integration.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.util.UuidUtil;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 历史语音播放鉴权集成测试。
 * <p>
 * 该测试锁定 task 3 的权限与失败边界：只有认证后的普通用户才能请求自己的历史播报，
 * 其他用户历史、未登录访问、功能开关关闭以及非 USER 角色都必须被稳定拒绝，且失败前不触发 provider。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "speech.enabled=true")
class AiSpeechAuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpeechProperties speechProperties;

    @MockitoBean
    private SpeechProvider speechProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        speechProperties.setEnabled(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证未登录请求会在安全链路被直接拒绝，避免匿名调用历史语音播放接口。
     */
    @Test
    void shouldRejectUnauthenticatedHistorySpeechRequest() throws Exception {
        mockMvc.perform(get("/api/ai/history/{id}/speech", UuidUtil.randomUuid()))
                .andExpect(status().isUnauthorized());

        verify(speechProvider, never()).synthesize(any());
    }

    /**
     * 验证普通用户只能访问自己的历史播报，请求他人历史时必须返回稳定业务错误而不是泄露内容。
     */
    @Test
    void shouldRejectOtherUsersHistorySpeech() throws Exception {
        User currentUser = createUser("spk-owner-user", "speech-owner-user@example.com", "USER");
        User otherUser = createUser("spk-owner-other", "speech-owner-other@example.com", "USER");
        String otherHistoryId = insertChatHistory(otherUser.getId(), "speech-owner-session", "他人的 AI 回复");

        mockMvc.perform(get("/api/ai/history/{id}/speech", otherHistoryId)
                        .header("Authorization", bearer(currentUser, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("AI 对话历史不存在"));

        verify(speechProvider, never()).synthesize(any());
    }

    /**
     * 验证语音功能关闭时，历史播报接口继续走统一业务错误语义，而不是因为记录是否存在出现分叉行为。
     */
    @Test
    void shouldRejectHistorySpeechWhenFeatureDisabled() throws Exception {
        speechProperties.setEnabled(false);
        User user = createUser("spk-disabled", "speech-disabled-user@example.com", "USER");

        mockMvc.perform(get("/api/ai/history/{id}/speech", UuidUtil.randomUuid())
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.FEATURE_DISABLED_MESSAGE));

        verify(speechProvider, never()).synthesize(any());
    }

    /**
     * 验证设备管理员不能使用历史语音播放入口，保护 AI 语音能力与 `/api/ai` 既有 USER 边界一致。
     */
    @Test
    void shouldRejectDeviceAdminUsingHistorySpeechEndpoint() throws Exception {
        User deviceAdmin = createUser("spk-dev-admin", "speech-history-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(get("/api/ai/history/{id}/speech", UuidUtil.randomUuid())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN")))
                .andExpect(status().isForbidden());

        verify(speechProvider, never()).synthesize(any());
    }

    /**
     * 创建指定角色的测试用户，确保权限测试聚焦在角色与 ownership，而不是账号冻结等外部状态。
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
        user.setPhone("13800138161");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 构造他人历史数据，专门用于保护“本人历史隔离”这条播放授权规则。
     */
    private String insertChatHistory(String userId, String sessionId, String aiResponse) {
        String historyId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                """
                        INSERT INTO chat_history (
                            id, user_id, session_id, user_input, ai_response, intent, intent_confidence,
                            extracted_info, execute_result, llm_model, response_time_ms
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                historyId,
                userId,
                sessionId,
                "测试输入",
                aiResponse,
                AiIntentType.QUERY.name(),
                BigDecimal.valueOf(0.92),
                "{\"source\":\"test\"}",
                AiExecuteResult.SUCCESS.name(),
                "mock-rule-provider",
                16);
        return historyId;
    }

    /**
     * 生成带角色信息的 Bearer Token，确保测试可以显式区分 USER 与 DEVICE_ADMIN 的安全链路差异。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
