package com.jhun.backend.integration.ai;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.common.enums.PromptTemplateType;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 对话接口集成测试。
 * <p>
 * 该测试锁定用户发起 AI 文本对话、设备管理员被拒绝以及对话历史落库三个关键契约，避免后续接入真实 LLM 时破坏当前规则降级闭环。
 */
@SpringBootTest
@ActiveProfiles("test")
class AiControllerIntegrationTest {

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
     * 验证普通用户可以发起 AI 文本对话，且接口会写入一条历史记录保护后续历史页数据来源。
     */
    @Test
    void shouldAllowUserToChatAndPersistHistory() throws Exception {
        User user = createUser("ai-user", "ai-user@example.com", "USER");
        String sessionId = "123e4567-e89b-42d3-a456-426614174000";

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

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_history WHERE user_id = ? AND session_id = ? AND intent = ?",
                Integer.class,
                user.getId(),
                sessionId,
                AiIntentType.RESERVE.name());
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    /**
     * 验证设备管理员不能使用 AI 对话接口，保护角色边界不被越权绕过。
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
    }

    /**
     * 验证当同一类型存在多条启用模板脏数据时，AI 运行时会 fail-fast，而不是悄悄挑一条继续执行。
     */
    @Test
    void shouldFailFastWhenPromptTemplateTypeHasMultipleActiveRecords() throws Exception {
        User user = createUser("ai-user-dup", "ai-user-dup@example.com", "USER");
        insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK, "dup-result-1");
        insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK, "dup-result-2");

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "帮我查询一下设备状态"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("同一 Prompt 模板类型存在多条启用模板，请先清理脏数据"));
    }

    /**
     * 创建指定角色的测试用户。
     * <p>
     * 这里显式把用户状态设为可登录、冻结状态设为 `NORMAL`，用于隔离“角色边界”与“账户不可用”两类失败原因，
     * 确保 AI 对话测试真正保护的是 USER 可用、DEVICE_ADMIN 被拒绝这一权限契约。
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
     * 为测试用户生成 Bearer Token。
     * <p>
     * Token 中的角色声明会直接影响安全链路和方法鉴权结果，因此这里由调用方显式传入角色，
     * 避免测试误把数据库角色和 JWT 角色混为一谈而掩盖权限问题。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    /**
     * 插入启用中的 Prompt 模板脏数据，专门用于验证运行时冲突检测不会静默选中任意一条模板。
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
}
