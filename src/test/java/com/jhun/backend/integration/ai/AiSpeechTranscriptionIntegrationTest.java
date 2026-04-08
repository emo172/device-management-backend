package com.jhun.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.integration.ai.support.WavTestFixtures;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.IflytekSpeechProvider;
import com.jhun.backend.service.support.speech.IflytekSpeechWebSocketClient;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.jhun.backend.service.support.speech.WavPcmAudioParser;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AI 语音转写接口集成测试。
 * <p>
 * 该测试锁定 task 6 的正式成功链路：认证用户上传合法 PCM-WAV 后，
 * 后端会先剥离 WAV 头为裸 PCM，再走 `IflytekSpeechProvider -> IflytekSpeechWebSocketClient`，
 * 最终返回固定 `provider=iflytek` 的公共响应，而不是回退旧 Azure 路径或透出内部 provider 名称。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "speech.enabled=true",
        "speech.provider=iflytek",
        "speech.iflytek.app-id=test-app-id",
        "speech.iflytek.api-key=test-api-key",
        "speech.iflytek.api-secret=test-api-secret"
})
class AiSpeechTranscriptionIntegrationTest {

    private static final String MOCK_CLIENT_PROVIDER = "mock-client-provider";

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

    @MockitoBean
    private IflytekSpeechWebSocketClient iflytekSpeechWebSocketClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证普通用户可以上传受支持的 PCM-WAV 录音并拿到固定 locale 的转写结果。
     * <p>
     * 这里故意把 multipart 头写成 `audio/x-wav`，
     * 以防服务层再次退化成只认 `audio/wav` 单一字面量，错拒等价 WAV 变体。
     */
    @Test
    void shouldTranscribePcmWavForAuthenticatedUser() throws Exception {
        User user = createUser("speech-tx-user", "speech-transcribe-user@example.com", "USER");
        byte[] wavBytes = WavTestFixtures.validMono16Bit16KhzWav(3200);
        byte[] expectedPcmBytes = new byte[wavBytes.length - 44];
        System.arraycopy(wavBytes, 44, expectedPcmBytes, 0, expectedPcmBytes.length);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.wav",
                "audio/x-wav",
                wavBytes);
        when(iflytekSpeechWebSocketClient.transcribe(any(), any(), any())).thenReturn(new SpeechTranscriptionResult(
                "帮我预约明天下午两点的会议室",
                SpeechContract.LOCALE_ZH_CN,
                MOCK_CLIENT_PROVIDER));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(file)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcript").value("帮我预约明天下午两点的会议室"))
                .andExpect(jsonPath("$.data.locale").value(SpeechContract.LOCALE_ZH_CN))
                .andExpect(jsonPath("$.data.provider").value(IflytekSpeechProvider.IFLYTEK_PROVIDER));

        ArgumentCaptor<SpeechTranscriptionRequest> requestCaptor = ArgumentCaptor.forClass(SpeechTranscriptionRequest.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SpeechProperties.IflytekProperties> configCaptor = ArgumentCaptor.forClass(SpeechProperties.IflytekProperties.class);
        verify(iflytekSpeechWebSocketClient).transcribe(
                configCaptor.capture(),
                requestCaptor.capture(),
                providerCaptor.capture());
        assertEquals(WavPcmAudioParser.PROVIDER_PCM_CONTENT_TYPE, requestCaptor.getValue().contentType());
        assertEquals(SpeechContract.LOCALE_ZH_CN, requestCaptor.getValue().locale());
        assertArrayEquals(expectedPcmBytes, requestCaptor.getValue().audioBytes());
        assertTrue(requestCaptor.getValue().audioBytes().length < wavBytes.length);
        assertEquals("test-app-id", configCaptor.getValue().getAppId());
        assertEquals(IflytekSpeechProvider.IFLYTEK_PROVIDER, providerCaptor.getValue());
    }

    /**
     * 验证未携带认证信息时，请求会在安全链路被直接拒绝。
     */
    @Test
    void shouldRejectUnauthenticatedTranscriptionRequest() throws Exception {
        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                WavTestFixtures.validMono16Bit16KhzWav(1600))))
                .andExpect(status().isUnauthorized());
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
        user.setPhone("13800138131");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
