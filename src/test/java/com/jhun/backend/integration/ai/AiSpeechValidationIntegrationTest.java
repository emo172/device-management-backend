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
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.integration.ai.support.WavTestFixtures;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.support.speech.SpeechContract;
import com.jhun.backend.service.support.speech.SpeechProvider;
import com.jhun.backend.service.support.speech.SpeechProviderException;
import com.jhun.backend.service.support.speech.SpeechProviderTimeoutException;
import com.jhun.backend.util.UuidUtil;
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
 * 该测试锁定 task 3 的 WAV/PCM 失败路径，确保 MIME 别名、RIFF/WAVE、PCM 参数与 60 秒边界
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
     * 验证正式合同只接受 WAV 容器，避免把 mp4 或其他未验证容器误放进 v1 合同。
     */
    @Test
    void shouldRejectUnsupportedContentType() throws Exception {
        User user = createUser("speech-type-user", "speech-invalid-type-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.mp4",
                                "audio/mp4",
                                WavTestFixtures.validMono16Bit16KhzWav(1600)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectEmptyAudioFile() throws Exception {
        User user = createUser("speech-empty-user", "speech-empty-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                new byte[0]))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.EMPTY_AUDIO_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectInvalidRiffWaveHeader() throws Exception {
        User user = createUser("speech-invalid-riff", "speech-invalid-riff-user@example.com", "USER");
        byte[] brokenHeader = WavTestFixtures.replaceAscii(
                WavTestFixtures.validMono16Bit16KhzWav(1600), 0, "RIFX");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                brokenHeader))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectUnsupportedSampleRate() throws Exception {
        User user = createUser("speech-rate-user", "speech-invalid-rate-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                WavTestFixtures.wav(8000, 1, 16, 1, 800)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectUnsupportedChannelCount() throws Exception {
        User user = createUser("speech-channel-user", "speech-invalid-channel-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                WavTestFixtures.wav(16000, 2, 16, 1, 800)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectNonPcmWavEncoding() throws Exception {
        User user = createUser("speech-non-pcm-user", "speech-non-pcm-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                WavTestFixtures.wav(16000, 1, 16, 3, 800)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.UNSUPPORTED_CONTENT_TYPE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectOversizedAudioFile() throws Exception {
        User user = createUser("speech-large-user", "speech-large-file-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                new byte[(int) SpeechContract.MAX_UPLOAD_SIZE_BYTES + 1]))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.FILE_TOO_LARGE_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectAudioLongerThanSixtySeconds() throws Exception {
        User user = createUser("speech-long-user", "speech-long-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wav",
                                WavTestFixtures.validMono16Bit16KhzWav(
                                        SpeechContract.INPUT_SAMPLE_RATE_HZ
                                                * (SpeechContract.MAX_RECORDING_DURATION_SECONDS + 1))))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.RECORDING_TOO_LONG_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldReturnControlledErrorWhenProviderTimesOut() throws Exception {
        User user = createUser("speech-time-user", "speech-timeout-user@example.com", "USER");
        when(speechProvider.transcribe(any())).thenThrow(new SpeechProviderTimeoutException("mock timeout"));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/wave",
                                WavTestFixtures.validMono16Bit16KhzWav(1600)))
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.TRANSCRIPTION_TIMEOUT_MESSAGE));
    }

    @Test
    void shouldReturnControlledErrorWhenProviderFails() throws Exception {
        User user = createUser("speech-fail-user", "speech-provider-fail-user@example.com", "USER");
        when(speechProvider.transcribe(any())).thenThrow(new SpeechProviderException("mock failure"));

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(new MockMultipartFile(
                                "file",
                                "voice.wav",
                                "audio/x-wav",
                                WavTestFixtures.validMono16Bit16KhzWav(1600)))
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
