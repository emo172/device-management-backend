package com.jhun.backend.integration.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "speech.enabled=true")
class AiSpeechAuthorizationIntegrationTest {

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

    @Test
    void shouldRejectUnauthenticatedSpeechTranscriptionRequest() throws Exception {
        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(validWavAudioFile()))
                .andExpect(status().isUnauthorized());

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectSpeechTranscriptionWhenFeatureDisabledForAuthenticatedUser() throws Exception {
        speechProperties.setEnabled(false);
        User user = createUser("spk-disabled", "speech-disabled-user@example.com", "USER");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(validWavAudioFile())
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SpeechContract.FEATURE_DISABLED_MESSAGE));

        verify(speechProvider, never()).transcribe(any());
    }

    @Test
    void shouldRejectDeviceAdminUsingSpeechEndpoint() throws Exception {
        User deviceAdmin = createUser("spk-dev-admin", "speech-device-admin@example.com", "DEVICE_ADMIN");

        mockMvc.perform(multipart("/api/ai/speech/transcriptions")
                        .file(validWavAudioFile())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN")))
                .andExpect(status().isForbidden());

        verify(speechProvider, never()).transcribe(any());
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
        user.setPhone("13800138161");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    private MockMultipartFile validWavAudioFile() {
        return new MockMultipartFile(
                "file",
                UuidUtil.randomUuid() + ".wav",
                "audio/wav",
                WavTestFixtures.validMono16Bit16KhzWav(1600));
    }
}
