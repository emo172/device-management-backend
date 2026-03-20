package com.jhun.backend.integration.device;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    /**
     * 设备图片上传测试必须写到系统临时目录，避免把运行产物落回仓库根下的 `uploads/` 并污染工作区。
     */
    private static final Path TEST_UPLOAD_DIR = createTestUploadDirectory();

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

    /**
     * 为整个测试类注入独立上传目录，确保静态资源映射仍走真实配置链路，但磁盘落点隔离到测试临时区。
     */
    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.upload-dir", () -> TEST_UPLOAD_DIR.toString());
        registry.add("storage.public-base-url", () -> "/files");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 在测试类结束后递归清理临时上传目录，避免本次运行遗留磁盘垃圾。
     */
    @AfterAll
    static void cleanUpUploadDirectory() throws IOException {
        deleteDirectoryRecursively(TEST_UPLOAD_DIR);
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
     * 验证设备图片上传后，即使客户端提交了带路径片段的文件名，
     * 服务端仍会返回 `/files/devices/` 前缀下的稳定公开地址，并允许匿名直接访问图片内容。
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
        byte[] expectedImageBytes = "fake-image".getBytes();

        MockMultipartFile image = new MockMultipartFile(
                "file",
                "../camera.png",
                MediaType.IMAGE_PNG_VALUE,
                expectedImageBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/devices/{id}/image", deviceId)
                        .file(image)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", startsWith("/files/devices/")))
                .andReturn();

        String imageUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data")
                .path("imageUrl")
                .asText();

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
                .andExpect(jsonPath("$.data.imageUrl").value(imageUrl))
                .andExpect(jsonPath("$.data.statusLogs[0].newStatus").value("MAINTENANCE"));

        /*
         * 设备列表卡片同样依赖后端回传的图片地址，
         * 因此这里补充校验列表响应继续透出 `/files/devices/**` 下的公开路径，避免前端在列表页回退到旧上传口径。
         */
        mockMvc.perform(get("/api/devices")
                        .header("Authorization", token)
                        .param("page", "1")
                        .param("size", "10")
                        .param("categoryName", categoryName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].imageUrl").value(imageUrl));

        /*
         * `/files/devices/**` 是前端展示设备图片的统一静态出口，因此这里额外校验匿名访问也能直接取回上传内容，
         * 防止图片 URL 虽然回写成功，但仍被安全链或资源映射挡住导致浏览器实际无法展示。
         */
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(expectedImageBytes));
    }

    /**
     * 验证同一设备重复上传图片时，不会在磁盘上持续堆积旧文件。
     * <p>
     * 允许实现选择“复用稳定路径”或“删除旧图再切换新路径”，
     * 但无论采用哪种策略，都必须保证设备图片目录里最终只保留该设备的当前有效图片。
     */
    @Test
    void shouldReplacePreviousDeviceImageWithoutLeavingOrphanFiles() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-4c", "device-admin-4c@example.com"), "DEVICE_ADMIN");
        String categoryName = "设备图片-去孤儿文件";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "切图相机",
                                  "deviceNumber": "DEV-CAM-REPLACE-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "验证重复上传会清理旧图",
                                  "location": "Studio-2"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                MediaType.IMAGE_PNG_VALUE,
                "first-image".getBytes());
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "second-image".getBytes());

        String firstImageUrl = objectMapper.readTree(mockMvc.perform(multipart("/api/devices/{id}/image", deviceId)
                        .file(firstImage)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", startsWith("/files/devices/")))
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data")
                .path("imageUrl")
                .asText();

        String secondImageUrl = objectMapper.readTree(mockMvc.perform(multipart("/api/devices/{id}/image", deviceId)
                        .file(secondImage)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", startsWith("/files/devices/")))
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data")
                .path("imageUrl")
                .asText();

        org.junit.jupiter.api.Assertions.assertEquals(1L, countDeviceImageFiles(deviceId));

        if (!firstImageUrl.equals(secondImageUrl)) {
            MvcResult staleImageResult = mockMvc.perform(get(firstImageUrl))
                    .andReturn();

            /*
             * 旧图路径一旦与新图不同，就不能继续返回原始图片内容；
             * 这里允许底层返回 404 或统一异常响应，但绝不能还是 200，否则说明旧文件仍在公开目录中可读。
             */
            org.junit.jupiter.api.Assertions.assertNotEquals(200, staleImageResult.getResponse().getStatus());
        }

        mockMvc.perform(get(secondImageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes("second-image".getBytes()));
    }

    /**
     * 验证公开静态目录不会接受任意文件类型，避免把脚本等非图片文件直接暴露到 `/files/devices/`。
     */
    @Test
    void shouldRejectUnsupportedPublicFileTypeOnDeviceImageUpload() throws Exception {
        String token = bearer(createDeviceAdminUser("device-admin-4b", "device-admin-4b@example.com"), "DEVICE_ADMIN");
        String categoryName = "设备图片-类型白名单";
        createCategory(token, categoryName);

        MvcResult createResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "录音笔",
                                  "deviceNumber": "DEV-REC-001",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "验证公开文件类型白名单",
                                  "location": "Studio-1"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();
        MockMultipartFile scriptFile = new MockMultipartFile(
                "file",
                "deploy.sh",
                MediaType.TEXT_PLAIN_VALUE,
                "#!/bin/sh".getBytes());

        mockMvc.perform(multipart("/api/devices/{id}/image", deviceId)
                        .file(scriptFile)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
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
        String categoryAdminToken = bearer(createDeviceAdminUser("device-cat-admin-1", "device-cat-admin-1@example.com"), "DEVICE_ADMIN");
        String categoryName = "系统管理员无权设备创建";
        /*
         * 分类创建口径在 Task 3 中已经收敛到 DEVICE_ADMIN，
         * 因此这里先由设备管理员准备合法分类，再验证 SYSTEM_ADMIN 仍然不能越权创建设备。
         */
        createCategory(categoryAdminToken, categoryName);

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

    /**
     * 为上传测试创建系统临时目录，确保路径隔离到仓库外部。
     */
    private static Path createTestUploadDirectory() {
        try {
            return Files.createTempDirectory("device-controller-it-uploads-");
        } catch (IOException exception) {
            throw new IllegalStateException("创建设备图片测试临时目录失败", exception);
        }
    }

    /**
     * 递归删除测试临时目录，避免集成测试在本地持续累积上传产物。
     */
    private static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * 统计某台设备在测试上传目录里仍然残留的图片文件数量，
     * 用于直接保护“重复上传不会制造孤儿文件”的磁盘层契约。
     */
    private long countDeviceImageFiles(String deviceId) throws IOException {
        Path deviceImageDirectory = TEST_UPLOAD_DIR.resolve("devices");
        if (Files.notExists(deviceImageDirectory)) {
            return 0L;
        }
        try (var paths = Files.list(deviceImageDirectory)) {
            return paths.filter(path -> path.getFileName().toString().startsWith(deviceId)).count();
        }
    }
}
