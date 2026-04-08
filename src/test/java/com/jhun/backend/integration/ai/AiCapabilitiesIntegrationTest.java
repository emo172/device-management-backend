package com.jhun.backend.integration.ai;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 能力接口集成测试。
 * <p>
 * 该测试专门保护 `/api/ai/capabilities` 的最小响应契约与 USER 专属访问边界，
 * 防止后续把额外运行时细节暴露给前端，或无意放宽为管理员也可访问的入口。
 */
@SpringBootTest
@ActiveProfiles("test")
class AiCapabilitiesIntegrationTest {

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
    private AiRuntimeProperties aiRuntimeProperties;

    @Autowired
    private SpeechProperties speechProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证普通用户可以读取当前运行时 AI 能力，并且响应体只暴露前端真正需要的两个布尔字段。
     * `speechEnabled` 在这里仅表示语音输入转写是否可用，不额外派生历史播放或语音输出能力字段。
     */
    @Test
    void shouldReturnMinimalAiCapabilitiesForUser() throws Exception {
        User user = createUser("ai-cap-user", "ai-capabilities-user@example.com", "USER");

        mockMvc.perform(get("/api/ai/capabilities")
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.chatEnabled").value(aiRuntimeProperties.isChatEnabled()))
                .andExpect(jsonPath("$.data.ttsEnabled").doesNotExist())
                .andExpect(jsonPath("$.data.historySpeechEnabled").doesNotExist())
                .andExpect(jsonPath("$.data.speechEnabled").value(speechProperties.isTranscriptionAvailable()));
    }

    /**
     * 验证即使显式打开 `speech.enabled`，只要一期讯飞凭据缺失，`speechEnabled` 也必须回落为 `false`。
     * <p>
     * 这个场景专门防止接口只回显开关本身，导致前端误展示录音入口，用户第一次真正上传时才发现运行时根本不可用。
     */
    @Test
    void shouldReportSpeechDisabledWhenIflytekCredentialsAreIncomplete() throws Exception {
        User user = createUser("ai-cap-speech-mis", "ai-capabilities-speech-missing@example.com", "USER");
        String originalApiSecret = speechProperties.getIflytek().getApiSecret();
        boolean originalEnabled = speechProperties.isEnabled();
        try {
            speechProperties.setEnabled(true);
            speechProperties.getIflytek().setApiSecret("");

            mockMvc.perform(get("/api/ai/capabilities")
                            .header("Authorization", bearer(user, "USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.speechEnabled").value(false));
        } finally {
            speechProperties.setEnabled(originalEnabled);
            speechProperties.getIflytek().setApiSecret(originalApiSecret);
        }
    }

    /**
     * 验证未认证请求会先被安全链拦截为 401，而不是穿透到控制器层拿到伪造的默认能力值。
     */
    @Test
    void shouldRequireAuthenticationWhenGettingAiCapabilities() throws Exception {
        mockMvc.perform(get("/api/ai/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证设备管理员不能访问 USER 专属的 AI 能力接口，避免管理角色借该入口探测普通用户能力开关。
     */
    @Test
    void shouldRejectDeviceAdminWhenGettingAiCapabilities() throws Exception {
        User deviceAdmin = createUser("ai-cap-da", "ai-capabilities-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(get("/api/ai/capabilities")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证系统管理员同样不能访问 USER 专属的 AI 能力接口，保护 `/api/ai/*` 的既有角色边界不会被意外放宽。
     */
    @Test
    void shouldRejectSystemAdminWhenGettingAiCapabilities() throws Exception {
        User systemAdmin = createUser("ai-cap-sa", "ai-capabilities-system-admin@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(get("/api/ai/capabilities")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    /**
     * 创建可正常登录的测试用户。
     * <p>
     * 这里统一构造未冻结、可登录账户，用来把用例失败原因聚焦在角色鉴权本身，
     * 避免账号状态异常掩盖 `/api/ai/capabilities` 的真实访问边界。
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
        user.setPhone("13800138141");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 生成带角色声明的 Bearer Token。
     * <p>
     * 安全链会同时读取登录身份与 JWT 内的角色信息，因此这里显式由调用方传入角色，
     * 以便精确验证 USER/DEVICE_ADMIN/SYSTEM_ADMIN 三条访问路径的预期结果。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
