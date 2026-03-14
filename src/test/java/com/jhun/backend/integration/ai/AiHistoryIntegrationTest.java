package com.jhun.backend.integration.ai;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 历史接口集成测试。
 * <p>
 * 该测试重点保护“只能查看本人 AI 历史”的数据隔离规则，并验证列表与详情接口都能读取同一张历史表。
 */
@SpringBootTest
@ActiveProfiles("test")
class AiHistoryIntegrationTest {

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
     * 验证历史列表只返回当前登录人的记录，避免不同用户之间互相看到对话内容。
     */
    @Test
    void shouldListOnlyCurrentUserHistory() throws Exception {
        User currentUser = createUser("history-user", "history-user@example.com", "USER");
        User otherUser = createUser("history-user-other", "history-user-other@example.com", "USER");
        String currentHistoryId = insertChatHistory(
                currentUser.getId(),
                "session-history-001",
                AiIntentType.QUERY.name(),
                AiExecuteResult.SUCCESS.name());
        insertChatHistory(
                otherUser.getId(),
                "session-history-002",
                AiIntentType.HELP.name(),
                AiExecuteResult.SUCCESS.name());

        mockMvc.perform(get("/api/ai/history")
                        .header("Authorization", bearer(currentUser, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(currentHistoryId))
                .andExpect(jsonPath("$.data[0].intent").value(AiIntentType.QUERY.name()));
    }

    /**
     * 验证历史详情接口只能读取本人记录，请求他人记录时应返回业务失败而不是泄露数据。
     */
    @Test
    void shouldReturnOnlyOwnedHistoryDetail() throws Exception {
        User currentUser = createUser("history-detail-user", "history-detail-user@example.com", "USER");
        User otherUser = createUser("history-detail-other", "history-detail-other@example.com", "USER");
        String ownedHistoryId = insertChatHistory(
                currentUser.getId(),
                "session-history-011",
                AiIntentType.CANCEL.name(),
                AiExecuteResult.FAILED.name());
        String otherHistoryId = insertChatHistory(
                otherUser.getId(),
                "session-history-012",
                AiIntentType.HELP.name(),
                AiExecuteResult.SUCCESS.name());

        mockMvc.perform(get("/api/ai/history/{id}", ownedHistoryId)
                        .header("Authorization", bearer(currentUser, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownedHistoryId))
                .andExpect(jsonPath("$.data.executeResult").value(AiExecuteResult.FAILED.name()));

        mockMvc.perform(get("/api/ai/history/{id}", otherHistoryId)
                        .header("Authorization", bearer(currentUser, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("AI 对话历史不存在"));
    }

    /**
     * 验证历史列表存在固定上限，避免单次请求把用户全部历史记录一次性拉回。
     */
    @Test
    void shouldLimitAiHistoryListSize() throws Exception {
        User currentUser = createUser("history-limit-user", "history-limit-user@example.com", "USER");
        for (int index = 0; index < 25; index++) {
            insertChatHistory(
                    currentUser.getId(),
                    "session-history-limit-" + index,
                    AiIntentType.QUERY.name(),
                    AiExecuteResult.SUCCESS.name());
        }

        mockMvc.perform(get("/api/ai/history")
                        .header("Authorization", bearer(currentUser, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(20));
    }

    /**
     * 创建可正常登录的普通用户测试数据。
     * <p>
     * 历史接口关注的是“本人历史隔离”而不是账号状态控制，因此这里统一构造正常账户，
     * 避免测试因用户禁用、冻结等其他条件失败而削弱对历史隔离契约的保护力度。
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
        user.setPhone("13800138131");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 直接插入一条 AI 历史记录。
     * <p>
     * 该辅助方法通过最小字段建数来模拟不同用户名下的历史数据，重点保护列表只看本人、详情不能越权读取两条契约；
     * 其中 `intent` 和 `executeResult` 参数由测试显式传入，避免断言退化为只验证“有数据返回”。
     */
    private String insertChatHistory(String userId, String sessionId, String intent, String executeResult) {
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
                "测试回复",
                intent,
                BigDecimal.valueOf(0.95),
                "{\"source\":\"test\"}",
                executeResult,
                "mock-rule-provider",
                12);
        return historyId;
    }

    /**
     * 生成带角色信息的 Bearer Token。
     * <p>
     * 历史接口同时受认证与角色边界约束，因此测试中保持 token 角色显式可见，便于定位是本人隔离问题还是角色权限问题。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
