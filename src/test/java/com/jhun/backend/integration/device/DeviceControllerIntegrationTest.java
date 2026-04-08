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
     * 验证可预约设备搜索会在后端同时执行关键字匹配、分类后代展开与稳定分页。
     * <p>
     * 该场景刻意让一台设备通过设备名命中 `q`，另一台设备通过子分类名命中 `q`，
     * 并把 `categoryId` 锁定在父分类，确保创建页拿到的是后端真实搜索结果，而不是前端本地过滤的假象。
     */
    @Test
    void searchesReservableDevicesByKeywordAndCategory() throws Exception {
        String token = bearer(createDeviceAdminUser("dev-search-1", "device-admin-search-1@example.com"), "DEVICE_ADMIN");
        String rootCategoryId = UuidUtil.randomUuid();
        String keywordMissChildCategoryId = UuidUtil.randomUuid();
        String keywordHitChildCategoryId = UuidUtil.randomUuid();
        String unrelatedCategoryId = UuidUtil.randomUuid();

        insertCategory(rootCategoryId, "媒体设备-根", null, 1);
        insertCategory(keywordMissChildCategoryId, "音频设备-子类", rootCategoryId, 2);
        insertCategory(keywordHitChildCategoryId, "Camera 配件-子类", rootCategoryId, 3);
        insertCategory(unrelatedCategoryId, "办公设备-其他", null, 4);

        String matchByDeviceNameId = UuidUtil.randomUuid();
        String matchByCategoryNameId = UuidUtil.randomUuid();
        insertDevice(
                matchByDeviceNameId,
                "Alpha Camera",
                "DEV-SEARCH-001",
                rootCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 10, 0));
        insertDevice(
                matchByCategoryNameId,
                "Beta Recorder",
                "DEV-SEARCH-002",
                keywordHitChildCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 9, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Gamma Speaker",
                "DEV-SEARCH-003",
                keywordMissChildCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 8, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Delta Camera",
                "DEV-SEARCH-004",
                unrelatedCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 11, 0));

        mockMvc.perform(get("/api/devices/reservable")
                        .header("Authorization", token)
                        .param("startTime", "2026-04-10T10:00:00")
                        .param("endTime", "2026-04-10T12:00:00")
                        .param("q", "cAmErA")
                        .param("categoryId", rootCategoryId)
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(matchByDeviceNameId))
                .andExpect(jsonPath("$.data.records[0].name").value("Alpha Camera"))
                .andExpect(jsonPath("$.data.records[0].categoryId").value(rootCategoryId));

        mockMvc.perform(get("/api/devices/reservable")
                        .header("Authorization", token)
                        .param("startTime", "2026-04-10T10:00:00")
                        .param("endTime", "2026-04-10T12:00:00")
                        .param("q", "cAmErA")
                        .param("categoryId", rootCategoryId)
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(matchByCategoryNameId))
                .andExpect(jsonPath("$.data.records[0].name").value("Beta Recorder"))
                .andExpect(jsonPath("$.data.records[0].categoryId").value(keywordHitChildCategoryId))
                .andExpect(jsonPath("$.data.records[0].categoryName").value("Camera 配件-子类"));
    }

    /**
     * 验证可预约设备搜索会排除静态不可预约设备与目标时间窗内的冲突设备。
     * <p>
     * 这里同时覆盖 `reservation_device` 优先和旧 `reservation.device_id` 兜底两种冲突真相，
     * 防止 cutover 之后创建页把实际上已被占用或根本不可预约的设备继续暴露给前端。
     */
    @Test
    void excludesConflictingOrUnavailableDevices() throws Exception {
        String token = bearer(createDeviceAdminUser("dev-search-2", "device-admin-search-2@example.com"), "DEVICE_ADMIN");
        User reservationUser = createNormalUser("usr-search-1", "device-user-search-1@example.com");
        String rootCategoryId = UuidUtil.randomUuid();
        insertCategory(rootCategoryId, "可预约筛选分类", null, 1);

        String availableDeviceId = UuidUtil.randomUuid();
        String outsideWindowDeviceId = UuidUtil.randomUuid();
        String relationConflictDeviceId = UuidUtil.randomUuid();
        String legacyConflictDeviceId = UuidUtil.randomUuid();
        insertDevice(
                availableDeviceId,
                "Available Device",
                "DEV-RESERVABLE-001",
                rootCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 10, 0));
        insertDevice(
                outsideWindowDeviceId,
                "Outside Window Device",
                "DEV-RESERVABLE-002",
                rootCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 9, 0));
        insertDevice(
                relationConflictDeviceId,
                "Relation Conflict Device",
                "DEV-RESERVABLE-003",
                rootCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 8, 0));
        insertDevice(
                legacyConflictDeviceId,
                "Legacy Conflict Device",
                "DEV-RESERVABLE-004",
                rootCategoryId,
                "AVAILABLE",
                LocalDateTime.of(2026, 4, 6, 7, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Borrowed Device",
                "DEV-RESERVABLE-005",
                rootCategoryId,
                "BORROWED",
                LocalDateTime.of(2026, 4, 6, 6, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Maintenance Device",
                "DEV-RESERVABLE-006",
                rootCategoryId,
                "MAINTENANCE",
                LocalDateTime.of(2026, 4, 6, 5, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Disabled Device",
                "DEV-RESERVABLE-007",
                rootCategoryId,
                "DISABLED",
                LocalDateTime.of(2026, 4, 6, 4, 0));
        insertDevice(
                UuidUtil.randomUuid(),
                "Deleted Device",
                "DEV-RESERVABLE-008",
                rootCategoryId,
                "DELETED",
                LocalDateTime.of(2026, 4, 6, 3, 0));

        String relationFirstReservationId = UuidUtil.randomUuid();
        insertReservation(
                relationFirstReservationId,
                availableDeviceId,
                reservationUser.getId(),
                LocalDateTime.of(2026, 4, 10, 10, 30),
                LocalDateTime.of(2026, 4, 10, 11, 30),
                "APPROVED");
        insertReservationDevice(relationFirstReservationId, relationConflictDeviceId, 0);

        insertReservation(
                UuidUtil.randomUuid(),
                legacyConflictDeviceId,
                reservationUser.getId(),
                LocalDateTime.of(2026, 4, 10, 10, 45),
                LocalDateTime.of(2026, 4, 10, 11, 15),
                "PENDING_DEVICE_APPROVAL");

        insertReservation(
                UuidUtil.randomUuid(),
                outsideWindowDeviceId,
                reservationUser.getId(),
                LocalDateTime.of(2026, 4, 10, 12, 30),
                LocalDateTime.of(2026, 4, 10, 13, 30),
                "APPROVED");

        mockMvc.perform(get("/api/devices/reservable")
                        .header("Authorization", token)
                        .param("startTime", "2026-04-10T10:00:00")
                        .param("endTime", "2026-04-10T12:00:00")
                        .param("categoryId", rootCategoryId)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(availableDeviceId))
                .andExpect(jsonPath("$.data.records[1].id").value(outsideWindowDeviceId));
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
        MvcResult primaryDeviceCreateResult = mockMvc.perform(post("/api/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "多设备预约主设备",
                                  "deviceNumber": "DEV-MAINT-002",
                                  "categoryName": "%s",
                                  "status": "AVAILABLE",
                                  "description": "维修通知主设备兼容测试",
                                  "location": "Studio-3"
                                }
                                """.formatted(categoryName)))
                .andExpect(status().isOk())
                .andReturn();

        String primaryDeviceId = objectMapper.readTree(primaryDeviceCreateResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();
        insertApprovedFutureReservationWithRelation(
                primaryDeviceId,
                deviceId,
                affectedUser.getId(),
                LocalDateTime.of(2026, 4, 10, 10, 0, 0));

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

    /**
     * 直接写入分类数据，便于搜索场景精确控制父子层级关系，而不把测试关注点分散到分类创建接口本身。
     */
    private void insertCategory(String categoryId, String name, String parentId, int sortOrder) {
        jdbcTemplate.update(
                "INSERT INTO device_category (id, name, parent_id, sort_order, description, default_approval_mode, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                categoryId,
                name,
                parentId,
                sortOrder,
                name + "-描述",
                "DEVICE_ONLY",
                LocalDateTime.of(2026, 4, 6, 8, 0),
                LocalDateTime.of(2026, 4, 6, 8, 0));
    }

    /**
     * 直接写入设备数据，用固定 created_at 控制搜索结果顺序，避免分页断言被数据库默认时间抖动影响。
     */
    private void insertDevice(
            String deviceId,
            String name,
            String deviceNumber,
            String categoryId,
            String status,
            LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO device (id, name, device_number, category_id, status, approval_mode_override, image_url, description, purchase_date, location, status_change_reason, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                deviceId,
                name,
                deviceNumber,
                categoryId,
                status,
                null,
                null,
                name + "-描述",
                null,
                "SEARCH-ROOM",
                null,
                createdAt,
                createdAt);
    }

    /**
     * 直接写入预约聚合，便于搜索测试独立控制时间窗冲突数据，而不再依赖完整预约审批流程。
     */
    private void insertReservation(
            String reservationId,
            String legacyDeviceId,
            String userId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status) {
        jdbcTemplate.update(
                "INSERT INTO reservation (id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, remark, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reservationId,
                userId,
                userId,
                "SELF",
                legacyDeviceId,
                startTime,
                endTime,
                "搜索测试预约",
                status,
                "DEVICE_ONLY",
                "NOT_CHECKED_IN",
                "搜索测试备注",
                startTime.minusDays(1),
                startTime.minusDays(1));
    }

    /**
     * 写入预约与设备关联，验证搜索冲突检测已经切到 `reservation_device` 为正式真相源。
     */
    private void insertReservationDevice(String reservationId, String deviceId, int deviceOrder) {
        jdbcTemplate.update(
                "INSERT INTO reservation_device (id, reservation_id, device_id, device_order, created_at) VALUES (?, ?, ?, ?, ?)",
                UuidUtil.randomUuid(),
                reservationId,
                deviceId,
                deviceOrder,
                LocalDateTime.of(2026, 4, 9, 8, 0));
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
     * 直接写入未来审批通过的多设备预约。
     * <p>
     * 这里故意让 `reservation.device_id` 停在主设备兼容列，而把真正进入维修的目标设备放进 `reservation_device`，
     * 用来证明维修通知查询已经切到关联表真相，而不是继续只看旧列。
     */
    private void insertApprovedFutureReservationWithRelation(
            String primaryDeviceId,
            String relatedDeviceId,
            String userId,
            LocalDateTime startTime) {
        String reservationId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                "INSERT INTO reservation (id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reservationId,
                userId,
                userId,
                "SELF",
                primaryDeviceId,
                startTime,
                startTime.plusHours(2),
                "维修通知测试预约",
                "APPROVED",
                "DEVICE_ONLY",
                "NOT_CHECKED_IN",
                LocalDateTime.of(2026, 4, 9, 8, 0, 0),
                LocalDateTime.of(2026, 4, 9, 8, 0, 0));
        insertReservationDevice(reservationId, primaryDeviceId, 0);
        insertReservationDevice(reservationId, relatedDeviceId, 1);
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
