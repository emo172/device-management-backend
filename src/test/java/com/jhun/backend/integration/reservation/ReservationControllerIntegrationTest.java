package com.jhun.backend.integration.reservation;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 预约控制器集成测试。
 * <p>
 * 用于覆盖预约创建、一审、二审与同人双审禁止规则，确保预约主链路状态机符合 SQL 新口径。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private DeviceCategoryMapper deviceCategoryMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证 DEVICE_ONLY 模式创建预约后进入待设备审批状态。
     */
    @Test
    void shouldCreateReservationWithDeviceApprovalStatus() throws Exception {
        User user = createUser("reserve-user-1", "reserve-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "2026-03-20T09:00:00",
                                  "endTime": "2026-03-20T10:00:00",
                                  "purpose": "课程演示",
                                  "remark": "第一条预约"
                                }
                                """.formatted(device.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_DEVICE_APPROVAL"));
    }

    /**
     * 验证 DEVICE_THEN_SYSTEM 模式下一审通过后进入待系统审批状态。
     */
    @Test
    void shouldMoveToSystemApprovalAfterFirstApproval() throws Exception {
        User user = createUser("reserve-user-2", "reserve-user-2@example.com", "USER");
        User deviceAdmin = createUser("reserve-device-admin", "reserve-device-admin@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_THEN_SYSTEM");

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "2026-03-20T11:00:00",
                                  "endTime": "2026-03-20T12:00:00",
                                  "purpose": "课程录制",
                                  "remark": "双审批预约"
                                }
                                """.formatted(device.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String reservationId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "设备管理员通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_SYSTEM_APPROVAL"));
    }

    /**
     * 验证同一账号不能完成双审两步，保护应用层双审隔离规则。
     */
    @Test
    void shouldRejectSecondApprovalBySameAccount() throws Exception {
        User user = createUser("reserve-user-3", "reserve-user-3@example.com", "USER");
        User dualRoleUser = createUser("reserve-dual-role", "reserve-dual-role@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_THEN_SYSTEM");

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "2026-03-20T13:00:00",
                                  "endTime": "2026-03-20T14:00:00",
                                  "purpose": "实验演示",
                                  "remark": "双审隔离测试"
                                }
                                """.formatted(device.getId())))
                .andExpect(status().isOk())
                .andReturn();

        String reservationId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(dualRoleUser, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "设备管理员通过"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reservations/{id}/system-audit", reservationId)
                        .header("Authorization", bearer(dualRoleUser, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "同账号尝试二审"
                                }
                                """))
                .andExpect(status().isBadRequest());
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
        user.setPhone("13800138444");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice(String approvalMode) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("预约分类-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 6));
        category.setSortOrder(1);
        category.setDescription("预约测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("预约设备-" + approvalMode);
        device.setDeviceNumber("RSV-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约测试设备");
        device.setLocation("Lab-Reservation");
        deviceMapper.insert(device);
        return device;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
