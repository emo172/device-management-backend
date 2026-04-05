package com.jhun.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.common.enums.AiExecuteResult;
import com.jhun.backend.common.enums.AiIntentType;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechSynthesisRequest;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import com.jhun.backend.util.UuidUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 历史语音播放集成测试。
 * <p>
 * 该测试锁定 task 3 的核心播放合同：只能读取当前用户自己的历史回复文本做中文 TTS，
 * 成功时返回非空 `audio/mpeg` 字节流；若历史回复为空或 provider 失败，则必须返回稳定业务错误。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "speech.enabled=true")
class AiSpeechPlaybackIntegrationTest {

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

    @MockitoBean
    private SpeechProvider speechProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证普通用户读取本人历史语音时，会把 `chat_history.ai_response` 按固定中文 TTS 契约交给 provider，
     * 并以不可缓存的 `audio/mpeg` 字节流直接返回。
     */
    @Test
    void shouldReturnOwnedHistorySpeechAsMp3Bytes() throws Exception {
        User user = createUser("spk-playback-usr", "speech-playback-user@example.com", "USER");
        String historyId = insertChatHistory(user.getId(), "speech-playback-session", "请在明天下午两点准时参加会议");
        byte[] audioBytes = "fake-mpeg-audio".getBytes(StandardCharsets.UTF_8);
        when(speechProvider.synthesize(any())).thenReturn(new SpeechSynthesisResult(
                audioBytes,
                SpeechContract.TTS_OUTPUT_CONTENT_TYPE,
                SpeechContract.PROVIDER_AZURE));

        mockMvc.perform(get("/api/ai/history/{id}/speech", historyId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, SpeechContract.TTS_OUTPUT_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(audioBytes));

        ArgumentCaptor<SpeechSynthesisRequest> requestCaptor = ArgumentCaptor.forClass(SpeechSynthesisRequest.class);
        verify(speechProvider).synthesize(requestCaptor.capture());
        assertEquals("请在明天下午两点准时参加会议", requestCaptor.getValue().text());
        assertEquals(SpeechContract.LOCALE_ZH_CN, requestCaptor.getValue().locale());
        assertEquals(SpeechContract.TTS_OUTPUT_CONTENT_TYPE, requestCaptor.getValue().outputFormat());
    }

    /**
     * 验证当历史记录没有可播报的 AI 回复时，会在服务层直接拒绝，避免把空文本交给 TTS provider。
     */
    @Test
    void shouldRejectHistoryWithoutAiResponse() throws Exception {
        User user = createUser("spk-empty-user", "speech-empty-history-user@example.com", "USER");
        String historyId = insertChatHistory(user.getId(), "speech-empty-session", "   ");

        mockMvc.perform(get("/api/ai/history/{id}/speech", historyId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.EMPTY_HISTORY_RESPONSE_MESSAGE));

        verify(speechProvider, never()).synthesize(any());
    }

    /**
     * 验证 provider 合成失败时，接口会返回稳定业务错误文案，而不是把底层供应商异常原样暴露给前端。
     */
    @Test
    void shouldReturnControlledErrorWhenPlaybackProviderFails() throws Exception {
        User user = createUser("spk-fail-user", "speech-playback-fail-user@example.com", "USER");
        String historyId = insertChatHistory(user.getId(), "speech-fail-session", "这是一段需要播报的回复");
        when(speechProvider.synthesize(any())).thenThrow(new SpeechProviderException("mock playback failure"));

        mockMvc.perform(get("/api/ai/history/{id}/speech", historyId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.SYNTHESIS_FAILED_MESSAGE));
    }

    /**
     * 创建可正常登录的测试用户，隔离账号状态影响，专注验证历史语音播放合同本身。
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
     * 直接插入 AI 历史记录，确保播放测试显式使用 `chat_history.ai_response` 作为 TTS 数据源。
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
                BigDecimal.valueOf(0.95),
                "{\"source\":\"test\"}",
                AiExecuteResult.SUCCESS.name(),
                "mock-rule-provider",
                18);
        return historyId;
    }

    /**
     * 生成带角色声明的 Bearer Token，确保测试显式区分认证身份与数据归属。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
