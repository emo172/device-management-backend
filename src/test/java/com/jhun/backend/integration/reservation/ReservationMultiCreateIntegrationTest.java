package com.jhun.backend.integration.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationDeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * 多设备单预约创建集成测试。
 * <p>
 * 该测试专门覆盖 T3 新增接口的最小闭环：
 * 一次请求只允许创建 1 条 reservation + N 条 reservation_device，任一设备校验失败都必须整单回滚，
 * 同时把阻塞原因通过 409 + blockingDevices[] 返回给前端。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationMultiCreateIntegrationTest {

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
    private ReservationMapper reservationMapper;
    @Autowired
    private ReservationDeviceMapper reservationDeviceMapper;
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
     * 验证 3 台设备会被整单写入成 1 条 reservation + 3 条 reservation_device，
     * 且成功响应要明确返回 deviceCount，避免前端把多设备预约误当成单设备预约处理。
     */
    @Test
    void createsAtomicMultiDeviceReservation() throws Exception {
        long beforeReservationCount = reservationMapper.selectCount(null);
        long beforeReservationDeviceCount = reservationDeviceMapper.selectCount(null);
        User user = createUser("multi-create-user", "USER");
        Device firstDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        Device secondDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        Device thirdDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(5, 9, 0);
        LocalDateTime endTime = startTime.plusHours(2);

        MvcResult result = mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(firstDevice.getId(), secondDevice.getId(), thirdDevice.getId()),
                                startTime,
                                endTime,
                                "多设备实验",
                                "整单成功")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(3))
                .andExpect(jsonPath("$.data.reservation.status").value("PENDING_DEVICE_APPROVAL"))
                .andExpect(jsonPath("$.data.reservation.deviceId").value(firstDevice.getId()))
                .andExpect(jsonPath("$.data.reservation.deviceName").value(firstDevice.getName()))
                .andExpect(jsonPath("$.data.reservation.reservationMode").value("SELF"))
                .andReturn();

        String reservationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("reservation")
                .path("id")
                .asText();

        assertEquals(beforeReservationCount + 1, reservationMapper.selectCount(null));
        assertEquals(beforeReservationDeviceCount + 3, reservationDeviceMapper.selectCount(null));
        assertEquals(3L, reservationDeviceMapper.countByReservationId(reservationId));
        assertEquals(firstDevice.getId(), reservationMapper.findAggregateById(reservationId).getDeviceId());
    }

    /**
     * 验证系统管理员可通过 targetUserId 代 USER 发起多设备预约，
     * 且整单模式必须保持 ON_BEHALF，避免新接口绕开既有代预约审计语义。
     */
    @Test
    void supportsProxyMultiReservationForSystemAdmin() throws Exception {
        User targetUser = createUser("multi-proxy-target", "USER");
        User systemAdmin = createUser("multi-proxy-admin", "SYSTEM_ADMIN");
        Device firstDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        Device secondDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(6, 10, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                targetUser.getId(),
                                List.of(firstDevice.getId(), secondDevice.getId()),
                                startTime,
                                endTime,
                                "系统管理员代预约",
                                "多设备代预约")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(2))
                .andExpect(jsonPath("$.data.reservation.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.data.reservation.createdBy").value(systemAdmin.getId()))
                .andExpect(jsonPath("$.data.reservation.reservationMode").value("ON_BEHALF"));
    }

    /**
     * 验证请求体里重复选择同一设备时，后端会在写事务前直接返回 DEVICE_DUPLICATED，
     * 避免前端误以为重复点击仍会生成一条有效的多设备预约。
     */
    @Test
    void rejectsDuplicatedDevices() throws Exception {
        long beforeReservationCount = reservationMapper.selectCount(null);
        long beforeReservationDeviceCount = reservationDeviceMapper.selectCount(null);
        User user = createUser("multi-duplicate-user", "USER");
        Device device = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(6, 12, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(device.getId(), device.getId()),
                                startTime,
                                endTime,
                                "重复设备",
                                "应失败")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("多设备预约参数校验失败"))
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value(device.getId()))
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_DUPLICATED"));

        assertEquals(beforeReservationCount, reservationMapper.selectCount(null));
        assertEquals(beforeReservationDeviceCount, reservationDeviceMapper.selectCount(null));
    }

    /**
     * 验证超过 10 台设备会被 DEVICE_LIMIT_EXCEEDED 拦截，
     * 确保“单预约最多 10 台”的限制由后端统一守住，而不是依赖前端页面本地约束。
     */
    @Test
    void rejectsWhenDeviceLimitExceeded() throws Exception {
        long beforeReservationCount = reservationMapper.selectCount(null);
        long beforeReservationDeviceCount = reservationDeviceMapper.selectCount(null);
        User user = createUser("multi-limit-user", "USER");
        List<String> deviceIds = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            deviceIds.add(createDevice("DEVICE_ONLY", "AVAILABLE").getId());
        }
        LocalDateTime startTime = futureTime(6, 14, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                deviceIds,
                                startTime,
                                endTime,
                                "超上限",
                                "应失败")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value(deviceIds.get(10)))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_LIMIT_EXCEEDED"));

        assertEquals(beforeReservationCount, reservationMapper.selectCount(null));
        assertEquals(beforeReservationDeviceCount, reservationDeviceMapper.selectCount(null));
    }

    /**
     * 验证只要其中 1 台设备冲突，整单就必须返回 409 并完全回滚，
     * 不能出现 reservation 已写入、但 reservation_device 只写了部分设备的半成功状态。
     */
    @Test
    void failsAtomicallyWhenAnyDeviceConflicts() throws Exception {
        User user = createUser("multi-conflict-user", "USER");
        Device conflictDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        Device freeDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(7, 9, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        createSingleReservation(user, conflictDevice, startTime, endTime, "冲突基线", "先占住一台设备");
        long beforeReservationCount = reservationMapper.selectCount(null);
        long beforeReservationDeviceCount = reservationDeviceMapper.selectCount(null);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(conflictDevice.getId(), freeDevice.getId()),
                                startTime,
                                endTime,
                                "整单冲突",
                                "应整单回滚")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value(conflictDevice.getId()))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_TIME_CONFLICT"));

        assertEquals(beforeReservationCount, reservationMapper.selectCount(null));
        assertEquals(beforeReservationDeviceCount, reservationDeviceMapper.selectCount(null));
    }

    /**
     * 验证不存在的设备会被明确标记成 DEVICE_NOT_FOUND，
     * 避免前端只能收到笼统报错却无法定位是哪台设备 ID 已失效。
     */
    @Test
    void rejectsWhenAnyDeviceDoesNotExist() throws Exception {
        User user = createUser("multi-missing-user", "USER");
        Device existingDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(7, 11, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(existingDevice.getId(), "missing-device-id"),
                                startTime,
                                endTime,
                                "设备不存在",
                                "应失败")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value("missing-device-id"))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_NOT_FOUND"));
    }

    /**
     * 验证静态不可预约状态会在事务前直接被拦截，
     * 防止 MAINTENANCE、DISABLED 等设备被错误写进 reservation_device。
     */
    @Test
    void rejectsWhenAnyDeviceIsNotReservable() throws Exception {
        User user = createUser("multi-not-reservable-user", "USER");
        Device unavailableDevice = createDevice("DEVICE_ONLY", "MAINTENANCE");
        Device availableDevice = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(7, 13, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(unavailableDevice.getId(), availableDevice.getId()),
                                startTime,
                                endTime,
                                "静态不可预约",
                                "应失败")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value(unavailableDevice.getId()))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_NOT_RESERVABLE"));
    }

    /**
     * 验证设备管理员不能通过新接口创建多设备预约，
     * 确保新能力不会绕开既有“DEVICE_ADMIN 不得创建预约”的角色边界。
     */
    @Test
    void rejectsMultiReservationForDeviceAdmin() throws Exception {
        User deviceAdmin = createUser("multi-device-admin", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY", "AVAILABLE");
        LocalDateTime startTime = futureTime(7, 15, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildMultiReservationPayload(
                                null,
                                List.of(device.getId()),
                                startTime,
                                endTime,
                                "权限边界",
                                "应失败")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.blockingDevices[0].deviceId").value(device.getId()))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_PERMISSION_DENIED"));
    }

    private void createSingleReservation(
            User user,
            Device device,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String purpose,
            String remark) throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "%s",
                                  "remark": "%s"
                                }
                                """.formatted(device.getId(), formatTime(startTime), formatTime(endTime), purpose, remark)))
                .andExpect(status().isOk());
    }

    private String buildMultiReservationPayload(
            String targetUserId,
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
        String targetUserSegment = targetUserId == null
                ? ""
                : "\n  \"targetUserId\": \"%s\",".formatted(targetUserId);
        return """
                {%s
                  "deviceIds": [%s],
                  "startTime": "%s",
                  "endTime": "%s",
                  "purpose": "%s",
                  "remark": "%s"
                }
                """.formatted(
                targetUserSegment,
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

    private Device createDevice(String approvalMode, String status) {
        String suffix = UuidUtil.randomUuid().substring(0, 8);
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("多设备预约分类-" + suffix);
        category.setSortOrder(1);
        category.setDescription("多设备预约测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("多设备预约设备-" + suffix);
        device.setDeviceNumber("MR-" + suffix);
        device.setCategoryId(category.getId());
        device.setStatus(status);
        device.setDescription("多设备预约测试设备");
        device.setLocation("Lab-Multi-Reservation");
        deviceMapper.insert(device);
        return device;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    /**
     * 统一把测试时间锚定到“当前整分钟”，避免固定日期随着真实时间推进而自然腐化。
     */
    private LocalDateTime alignedNow() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * 生成未来某天固定时分的测试时间，保证预约时间既可读又不会因年份变化导致用例失效。
     */
    private LocalDateTime futureTime(int plusDays, int hour, int minute) {
        return alignedNow().plusDays(plusDays).withHour(hour).withMinute(minute);
    }

    private String formatTime(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS).format(ISO_SECOND_FORMATTER);
    }
}
