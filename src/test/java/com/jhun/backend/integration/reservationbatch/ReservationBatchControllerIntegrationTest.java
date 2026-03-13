package com.jhun.backend.integration.reservationbatch;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 预约批次控制器集成测试。
 * <p>
 * 本测试覆盖 Task 9 的核心边界：
 * 1) SYSTEM_ADMIN 才能代 USER 预约；
 * 2) USER 可发起本人批量预约，SYSTEM_ADMIN 可发起管理型批量预约；
 * 3) 批次汇总字段（总数、成功数、失败数、状态）可查询且与执行结果一致。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationBatchControllerIntegrationTest {

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
     * 验证设备管理员不能发起代预约，保护“DEVICE_ADMIN 不得提交预约”的角色边界。
     */
    @Test
    void shouldRejectProxyReservationByDeviceAdmin() throws Exception {
        User targetUser = createUser("rb-user-01", "rb-user-01@example.com", "USER");
        User deviceAdmin = createUser("rb-devadm-1", "rb-devadm-1@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY", "RB-DEV-01");

        mockMvc.perform(post("/api/reservations/proxy")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "%s",
                                  "deviceId": "%s",
                                  "startTime": "2026-03-25T09:00:00",
                                  "endTime": "2026-03-25T10:00:00",
                                  "purpose": "代预约边界校验",
                                  "remark": "设备管理员不允许代预约"
                                }
                                """.formatted(targetUser.getId(), device.getId())))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证系统管理员可以代 USER 预约，且预约模式必须落地为 ON_BEHALF。
     */
    @Test
    void shouldAllowSystemAdminProxyReservationForUser() throws Exception {
        User targetUser = createUser("rb-user-02", "rb-user-02@example.com", "USER");
        User systemAdmin = createUser("rb-sysadm-1", "rb-sysadm-1@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY", "RB-DEV-02");

        mockMvc.perform(post("/api/reservations/proxy")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "%s",
                                  "deviceId": "%s",
                                  "startTime": "2026-03-25T11:00:00",
                                  "endTime": "2026-03-25T12:00:00",
                                  "purpose": "系统管理员代预约",
                                  "remark": "用于联调 ON_BEHALF 模式"
                                }
                                """.formatted(targetUser.getId(), device.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.data.reservationMode").value("ON_BEHALF"))
                .andExpect(jsonPath("$.data.status").value("PENDING_DEVICE_APPROVAL"));
    }

    /**
     * 验证系统管理员不能代 DEVICE_ADMIN 预约，保护“仅可代 USER 预约”的业务边界。
     */
    @Test
    void shouldRejectProxyReservationForNonUserRole() throws Exception {
        User deviceAdminTarget = createUser("rb-devadm-2", "rb-devadm-2@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("rb-sysadm-2", "rb-sysadm-2@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY", "RB-DEV-03");

        mockMvc.perform(post("/api/reservations/proxy")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "%s",
                                  "deviceId": "%s",
                                  "startTime": "2026-03-25T13:00:00",
                                  "endTime": "2026-03-25T14:00:00",
                                  "purpose": "非法代预约对象",
                                  "remark": "系统管理员不应代设备管理员预约"
                                }
                                """.formatted(deviceAdminTarget.getId(), device.getId())))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证本人批量预约会生成批次并可回查汇总结果。
     */
    @Test
    void shouldCreateSelfBatchAndQuerySummary() throws Exception {
        User user = createUser("rb-user-03", "rb-user-03@example.com", "USER");
        Device firstDevice = createDevice("DEVICE_ONLY", "RB-DEV-04");
        Device secondDevice = createDevice("DEVICE_ONLY", "RB-DEV-05");

        MvcResult createResult = mockMvc.perform(post("/api/reservation-batches")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "%s",
                                  "items": [
                                    {
                                      "deviceId": "%s",
                                      "startTime": "2026-03-26T09:00:00",
                                      "endTime": "2026-03-26T10:00:00",
                                      "purpose": "批量预约-第一条",
                                      "remark": "本人批量预约"
                                    },
                                    {
                                      "deviceId": "%s",
                                      "startTime": "2026-03-26T10:30:00",
                                      "endTime": "2026-03-26T11:30:00",
                                      "purpose": "批量预约-第二条",
                                      "remark": "本人批量预约"
                                    }
                                  ]
                                }
                                """.formatted(user.getId(), firstDevice.getId(), secondDevice.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();

        JsonNode data = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        String batchId = data.path("id").asText();

        mockMvc.perform(get("/api/reservation-batches/{id}", batchId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(batchId))
                .andExpect(jsonPath("$.data.reservationCount").value(2));
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
        user.setPhone("13800138555");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice(String approvalMode, String deviceNumber) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("批次分类-" + deviceNumber);
        category.setSortOrder(1);
        category.setDescription("预约批次测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("批次设备-" + deviceNumber);
        device.setDeviceNumber(deviceNumber);
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约批次测试设备");
        device.setLocation("Lab-Reservation-Batch");
        deviceMapper.insert(device);
        return device;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
