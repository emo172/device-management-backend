package com.jhun.backend.integration.ai;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.util.UuidUtil;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 语音 feature flag 集成测试。
 * <p>
 * 该测试用于锁定 task 1 的两个核心基线：语音开关关闭时两个端点都返回统一业务错误，
 * 同时语音入口继续沿用现有 AI 的 USER 权限边界，避免后续实现把失败语义和越权语义混在一起。
 */
@SpringBootTest
@ActiveProfiles("test")
class AiSpeechFeatureFlagIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldRejectSpeechTranscriptionWhenFeatureDisabled() throws Exception {
        User user = createUser("speech-user", "speech-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.webm",
                                "audio/webm",
                                "fake-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value(SpeechContract.FEATURE_DISABLED_MESSAGE));
    }

    @Test
    void shouldRejectHistorySpeechWhenFeatureDisabled() throws Exception {
        User user = createUser("speech-history-user", "speech-history-user@example.com", "USER");

        mockMvc.perform(get("/api/ai/history/{id}/speech", UuidUtil.randomUuid())
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value(SpeechContract.FEATURE_DISABLED_MESSAGE));
    }

    @Test
    void shouldRejectDeviceAdminUsingSpeechEndpoint() throws Exception {
        User deviceAdmin = createUser("speech-device-admin", "speech-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.webm",
                                "audio/webm",
                                "fake-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN")))
                .andExpect(status().isForbidden());
    }

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

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
