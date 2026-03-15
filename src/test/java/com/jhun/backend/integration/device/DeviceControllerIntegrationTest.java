package com.jhun.backend.integration.device;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 设备控制器集成测试。
 * <p>
 * 用于验证设备编号唯一、CRUD、分页查询与软删除行为，确保设备页的主数据能力可联调。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeviceControllerIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证设备编号必须全局唯一，保护 SQL 中 device_number 唯一约束。
     */
    @Test
    void shouldRejectDuplicateDeviceNumber() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-1", "device-admin-1@example.com"), "DEVICE_ADMIN");
        String categoryName = "教学设备-编号";
        createCategory(token, categoryName);

        mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "投影仪 A",
                                  "deviceNumber": "DEV-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "第一台设备",
                                  "location": "A101"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "投影仪 B",
                                  "deviceNumber": "DEV-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "重复编号设备",
                                  "location": "A102"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证软删除后的设备不会继续出现在正常分页列表中。
     */
    @Test
    void shouldHideDeletedDeviceFromList() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-2", "device-admin-2@example.com"), "DEVICE_ADMIN");
        String categoryName = "实验设备-删除";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "示波器",
                                  "deviceNumber": "DEV-OSC-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "实验室设备",
                                  "location": "Lab-1"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String deviceId = body.path("data").path("id").asText();

        mockMvc.perform(delete("/api/devices/{id}", deviceId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        mockMvc.perform(get("/api/devices")
                        .header("Authorization", token)
                        .param("page", "1")
                        .param("size", "10")
                        .param("categoryName", categoryName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    /**
     * 验证设备可以被更新并在分页查询中按分类过滤返回。
     */
    @Test
    void shouldUpdateDeviceAndSupportCategoryFilter() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-3", "device-admin-3@example.com"), "DEVICE_ADMIN");
        String categoryName = "办公设备-过滤";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "打印机",
                                  "deviceNumber": "DEV-PRINT-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "初始说明",
                                  "location": "B201"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(put("/api/devices/{id}", deviceId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "彩色打印机",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "更新后的说明",
                                  "location": "B202"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("彩色打印机"));

        mockMvc.perform(get("/api/devices")
                        .header("Authorization", token)
                        .param("page", "1")
                        .param("size", "10")
                        .param("categoryName", categoryName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("彩色打印机"));
    }

    /**
     * 验证设备图片上传后可在详情中回传图片地址和状态日志，保护设备详情页的可追溯能力。
     */
    @Test
    void shouldUploadImageAndReturnStatusLogsInDetail() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-4", "device-admin-4@example.com"), "DEVICE_ADMIN");
        String categoryName = "设备图片-状态日志";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "摄像机",
                                  "deviceNumber": "DEV-CAM-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "需要图片与状态日志",
                                  "location": "Studio-1"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        MockMultipartFile image = new MockMultipartFile(
                "file",
                "camera.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image".getBytes());

        mockMvc.perform(multipart("/api/devices/{id}/image", deviceId)
                        .file(image)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").isNotEmpty());

        mockMvc.perform(put("/api/devices/{id}/status", deviceId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "MAINTENANCE",
                                  "reason": "镜头调试"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));

        mockMvc.perform(get("/api/devices/{id}", deviceId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.statusLogs[0].newStatus").value("MAINTENANCE"));
    }

    /**
     * 验证设备切换到 MAINTENANCE 时，会通知该设备未来已审批预约的受影响用户，避免用户到场后才发现设备不可用。
     */
    @Test
    void shouldSendMaintenanceNoticeToFutureApprovedReservationUsers() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-5", "device-admin-5@example.com"), "DEVICE_ADMIN");
        User affectedUser = createNormalUser("device-user-1", "device-user-1@example.com");
        String categoryName = "设备维修通知";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "三脚架",
                                  "deviceNumber": "DEV-MAINT-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "维修通知测试",
                                  "location": "Studio-2"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();
        insertApprovedFutureReservation(deviceId, affectedUser.getId(), LocalDateTime.of(2026, 4, 10, 10, 0, 0));

        mockMvc.perform(put("/api/devices/{id}/status", deviceId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "MAINTENANCE",
                                  "reason": "云台损坏，需要送修"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM notification_record WHERE user_id = ? AND notification_type = 'DEVICE_MAINTENANCE_NOTICE' AND related_id = ?",
                Long.class,
                affectedUser.getId(),
                deviceId);
        org.junit.jupiter.api.Assertions.assertEquals(1L, count == null ? 0L : count);
    }

    /**
     * 验证设备生命周期变更接口不再由 SYSTEM_ADMIN 驱动，避免越过 DEVICE_ADMIN 的职责边界。
     */
    @Test
    void shouldRejectCreateDeviceBySystemAdmin() throws Exception {
        String token = bearer(createSystemAdminUser("device-sys-1", "device-sys-1@example.com"), "SYSTEM_ADMIN");
        String categoryName = "系统管理员无权设备创建";
        createCategory(token, categoryName);

        mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "越权设备",
                                  "deviceNumber": "DEV-NOAUTH-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "系统管理员不应创建设备",
                                  "location": "SEC-1"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证借出中的设备不能被手工改成维修等其他状态，必须先走正式归还流程，避免破坏 BORROWED -> AVAILABLE 闭环。
     */
    @Test
    void shouldRejectManualMaintenanceTransitionWhenDeviceBorrowed() throws Exception {
        User deviceAdmin = createDeviceAdminUser("device-admin-6", "device-admin-6@example.com");
        String token = bearer(deviceAdmin, "DEVICE_ADMIN");
        String categoryName = "借出状态机保护";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "借出设备",
                                  "deviceNumber": "DEV-BORROWED-001",
                                  "categoryName": "%s",
                                  "status": "BORROWED",
                                  "description": "用于状态机回归",
                                  "location": "ROOM-1"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(put("/api/devices/{id}/status", deviceId)
                        .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "status": "MAINTENANCE",
                                  "reason": "不应允许手工从借出改到维修"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证通用编辑接口不能夹带 status 绕过专用状态更新入口，
     * 防止设备管理员借由基础信息编辑直接破坏 BORROWED -> AVAILABLE 的正式归还闭环。
     */
    @Test
    void shouldRejectStatusMutationThroughGeneralUpdateEndpoint() throws Exception {
        User deviceAdmin = createDeviceAdminUser("device-admin-7", "device-admin-7@example.com");
        String token = bearer(deviceAdmin, "DEVICE_ADMIN");
        String categoryName = "通用更新禁止改状态";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "绕过状态设备",
                                  "deviceNumber": "DEV-EDIT-STATUS-001",
                                  "categoryName": "%s",
                                  "status": "BORROWED",
                                  "description": "验证通用编辑不能改状态",
                                  "location": "ROOM-2"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(put("/api/devices/{id}", deviceId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "绕过状态设备-更新后",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "试图通过通用编辑改回可借",
                                  "location": "ROOM-3"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/devices/{id}", deviceId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BORROWED"))
                .andExpect(jsonPath("$.data.name").value("绕过状态设备"));
    }

    private void createCategory(String token, String name) throws Exception {
        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "分类说明",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(name)))
                .andExpect(status().isOk());
    }

    private User createDeviceAdminUser(String username, String email) {
        Role role = roleMapper.findByName("DEVICE_ADMIN");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138333");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private User createSystemAdminUser(String username, String email) {
        Role role = roleMapper.findByName("SYSTEM_ADMIN");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138333");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private User createNormalUser(String username, String email) {
        Role role = roleMapper.findByName("USER");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138334");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 直接写入未来审批通过预约，避免该测试再依赖完整预约审批链，确保关注点只落在维修通知联动本身。
     */
    private void insertApprovedFutureReservation(String deviceId, String userId, LocalDateTime startTime) {
        jdbcTemplate.update(
                "INSERT INTO reservation (id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UuidUtil.randomUuid(),
                userId,
                userId,
                "SELF",
                deviceId,
                startTime,
                startTime.plusHours(2),
                "维修通知测试预约",
                "APPROVED",
                "DEVICE_ONLY",
                "NOT_CHECKED_IN",
                LocalDateTime.of(2026, 4, 9, 8, 0, 0),
                LocalDateTime.of(2026, 4, 9, 8, 0, 0));
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
