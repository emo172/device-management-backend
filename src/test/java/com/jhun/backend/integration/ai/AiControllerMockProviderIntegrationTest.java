package com.jhun.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * mock provider 的 AI 对话控制器回归测试。
 * <p>
 * F1 审计指出 `/api/ai/chat` 已缺少 controller 级 mock 路径证据；
 * 本类专门在 `ai.provider=mock` 下重建这条回归防线，证明 provider 抽象与 qwen 接入后，
 * 原有的规则降级入口、历史留痕和 USER 角色边界仍然保持有效。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ai.enabled=true",
        "ai.provider=mock"
})
class AiControllerMockProviderIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证 `ai.provider=mock` 时，普通用户仍可通过 `/api/ai/chat` 走规则降级链路，且会写入 mock 历史记录。
     */
    @Test
    void shouldAllowUserToChatViaMockProviderAndPersistHistory() throws Exception {
        User user = createUser("ai-mock-user", "ai-mock-user@example.com", "USER");
        String sessionId = "423e4567-e89b-42d3-a456-426614174000";

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "message": "我想预约明天下午的设备"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.intent").value(AiIntentType.RESERVE.name()))
                .andExpect(jsonPath("$.data.executeResult").value(AiExecuteResult.PENDING.name()))
                .andExpect(jsonPath("$.data.aiResponse").isNotEmpty());

        MockHistoryRow history = jdbcTemplate.queryForObject(
                """
                        SELECT intent, execute_result, llm_model
                        FROM chat_history
                        WHERE user_id = ? AND session_id = ?
                        """,
                (resultSet, rowNum) -> new MockHistoryRow(
                        resultSet.getString("intent"),
                        resultSet.getString("execute_result"),
                        resultSet.getString("llm_model")),
                user.getId(),
                sessionId);
        assertThat(history).isNotNull();
        assertThat(history.intent()).isEqualTo(AiIntentType.RESERVE.name());
        assertThat(history.executeResult()).isEqualTo(AiExecuteResult.PENDING.name());
        assertThat(history.llmModel()).isEqualTo("mock-rule-provider");
    }

    /**
     * 验证即使切回 mock provider，设备管理员依然不能使用 AI 对话入口，防止 provider 切换时放宽 USER 专属边界。
     */
    @Test
    void shouldRejectDeviceAdminUsingAiChatViaMockProvider() throws Exception {
        User deviceAdmin = createUser("ai-mock-device-admin", "ai-mock-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "帮我查一下设备状态"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * 创建可正常登录的测试用户。
     * <p>
     * 这里统一构造未冻结且启用中的账号，避免 mock provider 回归测试被账户状态等无关因素打断，
     * 让断言真正只保护 provider 切换后的控制器合同。
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
        user.setPhone("13800138151");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 为测试用户生成 Bearer Token。
     * <p>
     * 角色声明直接影响控制器的权限判断，因此这里保持与数据库角色显式对齐，避免回归测试误掩盖鉴权问题。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    private record MockHistoryRow(String intent, String executeResult, String llmModel) {
    }
}
