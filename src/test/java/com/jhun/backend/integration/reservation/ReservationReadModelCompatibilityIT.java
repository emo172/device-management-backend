package com.jhun.backend.integration.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 预约读模型兼容性集成测试。
 * <p>
 * 该测试专门验证 T4 读模型扩展后的两个核心契约：
 * 1) 单设备与多设备预约都会同时暴露旧摘要字段和新的 `deviceCount/devices[]/primaryDevice*` 字段；
 * 2) 详情与列表读取以 `reservation_device` 为正式真相，即使 `reservation.device_id` 为空也仍然可读。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationReadModelCompatibilityIT {

    private static final DateTimeFormatter ISO_SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
     * 验证单设备与多设备预约在创建成功、列表和详情三个读入口上都能同时暴露旧字段与新字段。
     */
    @Test
    void exposesLegacyAndMultiDeviceFields() throws Exception {
        User user = createUser("read-model-user", "USER");
        Device singleDevice = createDevice("DEVICE_ONLY");
        Device multiPrimaryDevice = createDevice("DEVICE_ONLY");
        Device multiSecondaryDevice = createDevice("DEVICE_ONLY");

        LocalDateTime singleStartTime = futureTime(5, 9, 0);
        LocalDateTime multiStartTime = futureTime(5, 13, 0);

        MvcResult singleCreateResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "单设备兼容读模型",
                                  "remark": "创建成功回包应含新旧字段"
                                }
                                """.formatted(
                                singleDevice.getId(),
                                formatTime(singleStartTime),
                                formatTime(singleStartTime.plusHours(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(singleDevice.getId()))
                .andExpect(jsonPath("$.data.deviceName").value(singleDevice.getName()))
                .andExpect(jsonPath("$.data.deviceNumber").value(singleDevice.getDeviceNumber()))
                .andExpect(jsonPath("$.data.deviceCount").value(1))
                .andExpect(jsonPath("$.data.primaryDeviceId").value(singleDevice.getId()))
                .andExpect(jsonPath("$.data.primaryDeviceName").value(singleDevice.getName()))
                .andExpect(jsonPath("$.data.primaryDeviceNumber").value(singleDevice.getDeviceNumber()))
                .andExpect(jsonPath("$.data.devices.length()").value(1))
                .andExpect(jsonPath("$.data.devices[0].deviceId").value(singleDevice.getId()))
                .andReturn();
        String singleReservationId = objectMapper.readTree(singleCreateResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        MvcResult multiCreateResult = mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                List.of(multiPrimaryDevice.getId(), multiSecondaryDevice.getId()),
                                multiStartTime,
                                multiStartTime.plusHours(2),
                                "多设备兼容读模型",
                                "主设备兼容字段应稳定映射到第一台设备")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(2))
                .andExpect(jsonPath("$.data.reservation.deviceId").value(multiPrimaryDevice.getId()))
                .andExpect(jsonPath("$.data.reservation.deviceName").value(multiPrimaryDevice.getName()))
                .andExpect(jsonPath("$.data.reservation.deviceNumber").value(multiPrimaryDevice.getDeviceNumber()))
                .andExpect(jsonPath("$.data.reservation.deviceCount").value(2))
                .andExpect(jsonPath("$.data.reservation.primaryDeviceId").value(multiPrimaryDevice.getId()))
                .andExpect(jsonPath("$.data.reservation.primaryDeviceName").value(multiPrimaryDevice.getName()))
                .andExpect(jsonPath("$.data.reservation.primaryDeviceNumber").value(multiPrimaryDevice.getDeviceNumber()))
                .andExpect(jsonPath("$.data.reservation.devices.length()").value(2))
                .andExpect(jsonPath("$.data.reservation.devices[0].deviceId").value(multiPrimaryDevice.getId()))
                .andExpect(jsonPath("$.data.reservation.devices[1].deviceId").value(multiSecondaryDevice.getId()))
                .andReturn();
        String multiReservationId = objectMapper.readTree(multiCreateResult.getResponse().getContentAsString())
                .path("data")
                .path("reservation")
                .path("id")
                .asText();

        JsonNode listRoot = readResponseTree(mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(2, listRoot.path("data").path("total").asInt());
        JsonNode records = listRoot.path("data").path("records");
        assertReservationReadModel(
                findReservationNode(records, singleReservationId),
                List.of(singleDevice),
                1,
                "单设备兼容读模型");
        assertReservationReadModel(
                findReservationNode(records, multiReservationId),
                List.of(multiPrimaryDevice, multiSecondaryDevice),
                2,
                "多设备兼容读模型");

        JsonNode singleDetail = readResponseTree(mockMvc.perform(get("/api/reservations/{id}", singleReservationId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andReturn())
                .path("data");
        assertReservationReadModel(singleDetail, List.of(singleDevice), 1, "单设备兼容读模型");

        JsonNode multiDetail = readResponseTree(mockMvc.perform(get("/api/reservations/{id}", multiReservationId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andReturn())
                .path("data");
        assertReservationReadModel(multiDetail, List.of(multiPrimaryDevice, multiSecondaryDevice), 2, "多设备兼容读模型");
    }

    /**
     * 验证即使 legacy `reservation.device_id` 为空，详情与列表也能继续从 `reservation_device` 聚合出设备摘要。
     */
    @Test
    void readsFromAssociationTableAfterCutover() throws Exception {
        User user = createUser("cutover-user", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(6, 10, 0);

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "切换后读模型",
                                  "remark": "legacy device_id 为空也要可读"
                                }
                                """.formatted(
                                device.getId(),
                                formatTime(startTime),
                                formatTime(startTime.plusHours(1)))))
                .andExpect(status().isOk())
                .andReturn();
        String reservationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        String legacyDeviceId = jdbcTemplate.queryForObject(
                "SELECT device_id FROM reservation WHERE id = ?",
                String.class,
                reservationId);
        assertNull(legacyDeviceId, "新单预约写路径不应继续把 reservation.device_id 当成真相列回写");

        JsonNode detailNode = readResponseTree(mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andReturn())
                .path("data");
        assertReservationReadModel(detailNode, List.of(device), 1, "切换后读模型");

        JsonNode listNode = readResponseTree(mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn())
                .path("data")
                .path("records");
        assertReservationReadModel(findReservationNode(listNode, reservationId), List.of(device), 1, "切换后读模型");
    }

    private void assertReservationReadModel(
            JsonNode reservationNode,
            List<Device> expectedDevices,
            int expectedDeviceCount,
            String expectedPurpose) {
        assertNotNull(reservationNode);
        assertEquals(expectedPurpose, reservationNode.path("purpose").asText());
        assertEquals(expectedDeviceCount, reservationNode.path("deviceCount").asInt());

        Device primaryDevice = expectedDevices.getFirst();
        assertEquals(primaryDevice.getId(), reservationNode.path("deviceId").asText());
        assertEquals(primaryDevice.getName(), reservationNode.path("deviceName").asText());
        assertEquals(primaryDevice.getDeviceNumber(), reservationNode.path("deviceNumber").asText());
        assertEquals(primaryDevice.getId(), reservationNode.path("primaryDeviceId").asText());
        assertEquals(primaryDevice.getName(), reservationNode.path("primaryDeviceName").asText());
        assertEquals(primaryDevice.getDeviceNumber(), reservationNode.path("primaryDeviceNumber").asText());

        JsonNode devicesNode = reservationNode.path("devices");
        assertTrue(devicesNode.isArray());
        assertEquals(expectedDeviceCount, devicesNode.size());
        for (int index = 0; index < expectedDevices.size(); index++) {
            Device expectedDevice = expectedDevices.get(index);
            JsonNode deviceNode = devicesNode.get(index);
            assertEquals(expectedDevice.getId(), deviceNode.path("deviceId").asText());
            assertEquals(expectedDevice.getName(), deviceNode.path("deviceName").asText());
            assertEquals(expectedDevice.getDeviceNumber(), deviceNode.path("deviceNumber").asText());
        }
    }

    private JsonNode findReservationNode(JsonNode records, String reservationId) {
        Iterator<JsonNode> iterator = records.elements();
        while (iterator.hasNext()) {
            JsonNode candidate = iterator.next();
            if (reservationId.equals(candidate.path("id").asText())) {
                return candidate;
            }
        }
        throw new AssertionError("未在列表中找到预约: " + reservationId);
    }

    private JsonNode readResponseTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String buildMultiReservationPayload(
            List<String> deviceIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String purpose,
            String remark) {
        StringBuilder deviceArrayBuilder = new StringBuilder();
        for (int index = 0; index < deviceIds.size(); index++) {
            if (index > 0) {
                deviceArrayBuilder.append(", ");
            }
            deviceArrayBuilder.append('"').append(deviceIds.get(index)).append('"');
        }
        return """
                {
                  "deviceIds": [%s],
                  "startTime": "%s",
                  "endTime": "%s",
                  "purpose": "%s",
                  "remark": "%s"
                }
                """.formatted(
                deviceArrayBuilder,
                formatTime(startTime),
                formatTime(endTime),
                purpose,
                remark);
    }

    private User createUser(String prefix, String roleName) {
        String suffix = UuidUtil.randomUuid().substring(0, 8);
        String shortPrefix = prefix.length() > 8 ? prefix.substring(0, 8) : prefix;
        String username = shortPrefix + suffix.substring(0, 4);
        Role role = roleMapper.findByName(roleName);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(username + "-" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138" + suffix.substring(0, 3));
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice(String approvalMode) {
        String suffix = UuidUtil.randomUuid().substring(0, 8);
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("读模型分类-" + suffix);
        category.setSortOrder(1);
        category.setDescription("预约读模型测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("读模型设备-" + suffix);
        device.setDeviceNumber("READ-" + suffix);
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约读模型测试设备");
        device.setLocation("Lab-Read-Model");
        deviceMapper.insert(device);
        return device;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    private LocalDateTime alignedNow() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    private LocalDateTime futureTime(int plusDays, int hour, int minute) {
        return alignedNow().plusDays(plusDays).withHour(hour).withMinute(minute);
    }

    private String formatTime(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS).format(ISO_SECOND_FORMATTER);
    }
}
