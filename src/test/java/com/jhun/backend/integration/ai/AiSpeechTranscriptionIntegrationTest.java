package com.jhun.backend.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechTranscriptionRequest;
import com.jhun.backend.service.support.speech.SpeechTranscriptionResult;
import com.jhun.backend.util.UuidUtil;
import java.nio.charset.StandardCharsets;
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
 * 该测试锁定 task 2 的成功链路：认证用户上传浏览器协商出的 Ogg/Opus 录音后，
 * 后端会把音频字节、内容类型和固定中文 locale 交给独立 speech provider，并回传稳定响应结构。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "speech.enabled=true")
class AiSpeechTranscriptionIntegrationTest {

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
    private SpeechProvider speechProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证普通用户可以上传受支持的 Ogg/Opus 录音并拿到固定 locale 的转写结果。
     * <p>
     * 这里故意把 multipart 头写成 `audio/ogg; codecs=opus`，
     * 以防服务层再次退化成对空白差异不兼容的字符串硬匹配。
     */
    @Test
    void shouldTranscribeOggAudioForAuthenticatedUser() throws Exception {
        User user = createUser("speech-tx-user", "speech-transcribe-user@example.com", "USER");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.ogg",
                "audio/ogg; codecs=opus",
                "fake-ogg-audio".getBytes(StandardCharsets.UTF_8));
        when(speechProvider.transcribe(any())).thenReturn(new SpeechTranscriptionResult(
                "帮我预约明天下午两点的会议室",
                SpeechContract.LOCALE_ZH_CN,
                SpeechContract.PROVIDER_AZURE));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(file)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transcript").value("帮我预约明天下午两点的会议室"))
                .andExpect(jsonPath("$.data.locale").value(SpeechContract.LOCALE_ZH_CN))
                .andExpect(jsonPath("$.data.provider").value(SpeechContract.PROVIDER_AZURE));

        ArgumentCaptor<SpeechTranscriptionRequest> requestCaptor = ArgumentCaptor.forClass(SpeechTranscriptionRequest.class);
        verify(speechProvider).transcribe(requestCaptor.capture());
        assertEquals("audio/ogg;codecs=opus", requestCaptor.getValue().contentType());
        assertEquals(SpeechContract.LOCALE_ZH_CN, requestCaptor.getValue().locale());
    }

    /**
     * 验证未携带认证信息时，请求会在安全链路被直接拒绝。
     */
    @Test
    void shouldRejectUnauthenticatedTranscriptionRequest() throws Exception {
        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.ogg",
                                "audio/ogg",
                                "fake-ogg-audio".getBytes(StandardCharsets.UTF_8))))
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
