package com.jhun.backend.integration.borrow;

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
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
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
 * 多设备预约借还扇出兼容性测试。
 * <p>
 * 该测试类专门保护 T5 的 borrow/return 修复：
 * 确认借用时要为每台关联设备创建 borrow_record，确认归还时又必须按整张预约聚合一次性闭环。
 */
@SpringBootTest
@ActiveProfiles("test")
class BorrowWorkflowCompatibilityIT {

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
     * 验证多设备预约确认借用后，会为全部关联设备各创建 1 条 borrow_record。
     * <p>
     * 这条用例直接保护新的内部扇出语义，避免后续实现又退回“只给主设备建记录”的旧假设。
     */
    @Test
    void createsBorrowRecordsForAllAssociatedDevices() throws Exception {
        User user = createUser("brw-flow-u1", "borrow-workflow-user-1@example.com", "USER");
        User deviceAdmin = createUser("brw-flow-da1", "borrow-workflow-device-admin-1@example.com", "DEVICE_ADMIN");
        Device firstDevice = createDevice("DEVICE_ONLY");
        Device secondDevice = createDevice("DEVICE_ONLY");
        String reservationId = createApprovedAndCheckedInMultiReservation(
                user,
                deviceAdmin,
                List.of(firstDevice, secondDevice),
                LocalDateTime.of(2026, 4, 22, 10, 0, 0));

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-04-22T10:30:00",
                                  "borrowCheckStatus": "两台设备已一起交接",
                                  "remark": "多设备整单借出"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("BORROWED"));

        List<BorrowRow> borrowRows = loadBorrowRowsByReservationId(reservationId);
        org.junit.jupiter.api.Assertions.assertEquals(2, borrowRows.size());
        org.junit.jupiter.api.Assertions.assertTrue(borrowRows.stream().map(BorrowRow::deviceId).collect(Collectors.toSet())
                .containsAll(List.of(firstDevice.getId(), secondDevice.getId())));
        org.junit.jupiter.api.Assertions.assertTrue(borrowRows.stream().allMatch(row -> "BORROWED".equals(row.status())));
        org.junit.jupiter.api.Assertions.assertEquals(1L, countStatusLogsByDeviceId(firstDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(1L, countStatusLogsByDeviceId(secondDevice.getId()));
    }

    /**
     * 验证归还入口虽然接收单条 borrowRecordId，但会把整张预约聚合里的借还记录一起闭环。
     * <p>
     * 这样可以防止多设备预约被公开接口拆成“先归还 A，再归还 B”的部分成功流程。
     */
    @Test
    void returnsAllAssociatedBorrowRecordsAtomically() throws Exception {
        User user = createUser("brw-flow-u2", "borrow-workflow-user-2@example.com", "USER");
        User deviceAdmin = createUser("brw-flow-da2", "borrow-workflow-device-admin-2@example.com", "DEVICE_ADMIN");
        Device firstDevice = createDevice("DEVICE_ONLY");
        Device secondDevice = createDevice("DEVICE_ONLY");
        String reservationId = createApprovedAndCheckedInMultiReservation(
                user,
                deviceAdmin,
                List.of(firstDevice, secondDevice),
                LocalDateTime.of(2026, 4, 23, 10, 0, 0));

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-04-23T10:20:00",
                                  "borrowCheckStatus": "准备验证整单归还",
                                  "remark": "整单借出后归还"
                                }
                                """))
                .andExpect(status().isOk());

        List<BorrowRow> borrowRows = loadBorrowRowsByReservationId(reservationId);
        BorrowRow targetRow = borrowRows.get(1);

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", targetRow.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-04-23T11:30:00",
                                  "returnCheckStatus": "两台设备已一起回收",
                                  "remark": "整单归还"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(targetRow.id()))
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        List<BorrowRow> returnedRows = loadBorrowRowsByReservationId(reservationId);
        org.junit.jupiter.api.Assertions.assertTrue(returnedRows.stream().allMatch(row -> "RETURNED".equals(row.status())));
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", loadDeviceStatus(firstDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", loadDeviceStatus(secondDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(2L, countStatusLogsByDeviceId(firstDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(2L, countStatusLogsByDeviceId(secondDevice.getId()));
    }

    private String createApprovedAndCheckedInMultiReservation(
            User user,
            User deviceAdmin,
            List<Device> devices,
            LocalDateTime startTime) throws Exception {
        String reservationId = createMultiReservation(user, devices, startTime, startTime.plusHours(2));
        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "借还链路审批通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(startTime.plusMinutes(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN"));
        return reservationId;
    }

    private String createMultiReservation(User user, List<Device> devices, LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        String deviceIdsJson = devices.stream()
                .map(Device::getId)
                .map(deviceId -> "\"" + deviceId + "\"")
                .collect(Collectors.joining(", "));
        MvcResult result = mockMvc.perform(post("/api/reservations/multi")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceIds": [%s],
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "BorrowWorkflowCompatibilityIT",
                                  "remark": "验证 borrow fan-out"
                                }
                                """.formatted(deviceIdsJson, startTime, endTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(devices.size()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("reservation").path("id").asText();
    }

    private List<BorrowRow> loadBorrowRowsByReservationId(String reservationId) {
        return jdbcTemplate.query(
                "SELECT id, device_id, status FROM borrow_record WHERE reservation_id = ? ORDER BY created_at ASC, id ASC",
                this::mapBorrowRow,
                reservationId);
    }

    private String loadDeviceStatus(String deviceId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM device WHERE id = ?",
                String.class,
                deviceId);
    }

    private long countStatusLogsByDeviceId(String deviceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_status_log WHERE device_id = ?",
                Long.class,
                deviceId);
        return count == null ? 0L : count;
    }

    private BorrowRow mapBorrowRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new BorrowRow(
                resultSet.getString("id"),
                resultSet.getString("device_id"),
                resultSet.getString("status"));
    }

    private Device createDevice(String approvalMode) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("借还兼容测试分类-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 8));
        category.setDefaultApprovalMode(approvalMode);
        category.setSortOrder(1);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("借还兼容测试设备-" + UuidUtil.randomUuid().substring(0, 8));
        device.setDeviceNumber("BWF-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("BorrowWorkflowCompatibilityIT 测试设备");
        device.setLocation("Borrow-Compatibility-Lab");
        deviceMapper.insert(device);
        return device;
    }

    private User createUser(String username, String email, String roleCode) {
        Role role = roleMapper.findByName(roleCode);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setEmail(email);
        user.setRealName(username + "-实名");
        user.setPhone("1380000" + Math.abs(username.hashCode() % 10000));
        user.setRoleId(role.getId());
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String roleCode) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), roleCode);
    }

    private record BorrowRow(String id, String deviceId, String status) {
    }
}
