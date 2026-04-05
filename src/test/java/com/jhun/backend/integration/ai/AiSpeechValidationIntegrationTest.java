package com.jhun.backend.integration.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.util.UuidUtil;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * AI 语音转写校验集成测试。
 * <p>
 * 该测试锁定 task 2 的失败路径，确保不支持的音频类型、超大文件以及 provider 超时/失败
 * 都会返回稳定业务错误，并且校验失败时不会触发真正的 provider 调用。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "speech.enabled=true")
class AiSpeechValidationIntegrationTest {

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
     * 验证正式合同只接受 Ogg/Opus 录音，避免把 mp4 或其他未验证容器误放进 v1 合同。
     */
    @Test
    void shouldRejectUnsupportedContentType() throws Exception {
        User user = createUser("speech-type-user", "speech-invalid-type-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.mp4",
                                "audio/mp4",
                                "fake-mp4-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    /**
     * 验证 codecs 参数里若不包含 Opus，会在服务层被直接拒绝。
     * <p>
     * 这样可以避免前端传来 `audio/ogg` 但实际编码不是 Azure 当前正式支持路径时，
     * 还继续把不兼容字节流送进 SDK 才在更深层失败。
     */
    @Test
    void shouldRejectOggWithoutOpusCodec() throws Exception {
        User user = createUser("speech-codec-user", "speech-invalid-codec-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.ogg",
                                "audio/ogg;codecs=pcm",
                                "fake-ogg-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    /**
     * 验证 `audio/*` 这类通配 MIME 不会被当成 `audio/ogg` 合法变体放行。
     * <p>
     * 这样可以避免客户端只给出模糊音频大类时，服务层误把请求继续送进 provider，
     * 最终把“合同不匹配”伪装成第三方语音失败。
     */
    @Test
    void shouldRejectWildcardAudioContentType() throws Exception {
        User user = createUser("speech-wildcard-user", "speech-wildcard-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.bin",
                                "audio/*",
                                "fake-ambiguous-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    /**
     * 验证超过 10MB 的录音会被显式拒绝，避免超大上传继续进入 provider 调用链。
     */
    @Test
    void shouldRejectOversizedAudioFile() throws Exception {
        User user = createUser("speech-large-user", "speech-large-file-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.ogg",
                                "audio/ogg",
                                new byte[(int) SpeechContract.MAX_UPLOAD_SIZE_BYTES + 1]))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.FILE_TOO_LARGE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    /**
     * 验证 provider 超时会被翻译成稳定的可重试业务错误，而不是直接暴露底层异常。
     */
    @Test
    void shouldReturnControlledErrorWhenProviderTimesOut() throws Exception {
        User user = createUser("speech-time-user", "speech-timeout-user@example.com", "USER");
        when(speechProvider.transcribe(any())).thenThrow(new SpeechProviderTimeoutException("mock timeout"));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.ogg",
                                "audio/ogg",
                                "fake-ogg-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.TRANSCRIPTION_TIMEOUT_MESSAGE));
    }

    /**
     * 验证 provider 其他失败会被统一收口成安全文案，避免把供应商实现细节透出给前端。
     */
    @Test
    void shouldReturnControlledErrorWhenProviderFails() throws Exception {
        User user = createUser("speech-fail-user", "speech-provider-fail-user@example.com", "USER");
        when(speechProvider.transcribe(any())).thenThrow(new SpeechProviderException("mock failure"));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.ogg",
                                "audio/ogg",
                                "fake-ogg-audio".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.TRANSCRIPTION_FAILED_MESSAGE));
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
        user.setPhone("13800138141");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
