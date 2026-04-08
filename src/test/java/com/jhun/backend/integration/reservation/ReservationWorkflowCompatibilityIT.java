package com.jhun.backend.integration.reservation;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 多设备预约整单工作流兼容性测试。
 * <p>
 * 该测试类直接保护 T5 的核心目标：
 * 多设备预约在审批、签到、借用、归还阶段仍然只表现为一张 reservation 聚合，
 * 但借还域内部已经能按设备扇出记录且不会允许局部状态继续推进。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationWorkflowCompatibilityIT {

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
     * 验证多设备预约可以走完整单审批、签到、借用、归还闭环。
     * <p>
     * 断言重点不是“有没有创建多条内部记录”，而是外部入口始终只围绕同一 reservationId 运转，
     * 且内部 borrow_record / device 状态会在一次事务里一起成功或一起失败。
     */
    @Test
    void keepsAggregateWorkflowAtomic() throws Exception {
        User user = createUser("rsv-flow-u1", "reservation-workflow-user-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-flow-da1", "reservation-workflow-device-admin-1@example.com", "DEVICE_ADMIN");
        Device primaryDevice = createDevice("DEVICE_ONLY");
        Device secondaryDevice = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 20, 10, 0, 0);
        LocalDateTime endTime = startTime.plusHours(2);
        String reservationId = createMultiReservation(user, List.of(primaryDevice, secondaryDevice), startTime, endTime);

        approveReservation(reservationId, deviceAdmin);
        checkInReservation(reservationId, user, startTime.plusMinutes(20));

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-04-20T10:30:00",
                                  "borrowCheckStatus": "整单交接完成",
                                  "remark": "多设备整单借出"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("BORROWED"));

        List<BorrowRow> borrowedRows = loadBorrowRowsByReservationId(reservationId);
        org.junit.jupiter.api.Assertions.assertEquals(2, borrowedRows.size());
        org.junit.jupiter.api.Assertions.assertTrue(borrowedRows.stream().map(BorrowRow::deviceId).collect(Collectors.toSet())
                .containsAll(List.of(primaryDevice.getId(), secondaryDevice.getId())));
        org.junit.jupiter.api.Assertions.assertTrue(borrowedRows.stream().allMatch(row -> "BORROWED".equals(row.status())));
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", loadDeviceStatus(primaryDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", loadDeviceStatus(secondaryDevice.getId()));

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowedRows.getFirst().id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-04-20T11:40:00",
                                  "returnCheckStatus": "整单归还完成",
                                  "remark": "多设备整单归还"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(borrowedRows.getFirst().id()))
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        List<BorrowRow> returnedRows = loadBorrowRowsByReservationId(reservationId);
        org.junit.jupiter.api.Assertions.assertTrue(returnedRows.stream().allMatch(row -> "RETURNED".equals(row.status())));
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", loadDeviceStatus(primaryDevice.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", loadDeviceStatus(secondaryDevice.getId()));

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservationId))
                .andExpect(jsonPath("$.data.deviceCount").value(2))
                .andExpect(jsonPath("$.data.devices.length()").value(2))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN"));
    }

    /**
     * 验证一旦内部出现单设备分叉事实，正式归还入口会直接拒绝继续推进。
     * <p>
     * 这里故意手工制造“同一预约里一台设备已归还、另一台仍借出”的脏状态，
     * 用来证明公开接口不会把这种分叉继续合法化成新的半成功结果。
     */
    @Test
    void rejectsPerDeviceStateDivergence() throws Exception {
        User user = createUser("rsv-flow-u2", "reservation-workflow-user-2@example.com", "USER");
        User deviceAdmin = createUser("rsv-flow-da2", "reservation-workflow-device-admin-2@example.com", "DEVICE_ADMIN");
        Device firstDevice = createDevice("DEVICE_ONLY");
        Device secondDevice = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 21, 10, 0, 0);
        LocalDateTime endTime = startTime.plusHours(2);
        String reservationId = createMultiReservation(user, List.of(firstDevice, secondDevice), startTime, endTime);

        approveReservation(reservationId, deviceAdmin);
        checkInReservation(reservationId, user, startTime.plusMinutes(15));
        confirmBorrow(reservationId, deviceAdmin, LocalDateTime.of(2026, 4, 21, 10, 20, 0));

        List<BorrowRow> borrowedRows = loadBorrowRowsByReservationId(reservationId);
        BorrowRow alreadyDivergedRow = borrowedRows.get(1);
        BorrowRow targetRow = borrowedRows.getFirst();
        jdbcTemplate.update(
                "UPDATE borrow_record SET status = 'RETURNED', return_time = ?, updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 4, 21, 11, 0, 0),
                LocalDateTime.of(2026, 4, 21, 11, 0, 0),
                alreadyDivergedRow.id());
        jdbcTemplate.update(
                "UPDATE device SET status = 'AVAILABLE', status_change_reason = ?, updated_at = ? WHERE id = ?",
                "手工制造分叉状态",
                LocalDateTime.of(2026, 4, 21, 11, 0, 0),
                alreadyDivergedRow.deviceId());

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", targetRow.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-04-21T11:10:00",
                                  "returnCheckStatus": "不应允许局部归还",
                                  "remark": "拒绝分叉推进"
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", loadBorrowStatus(targetRow.id()));
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", loadDeviceStatus(targetRow.deviceId()));
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
                                  "purpose": "多设备整单工作流测试",
                                  "remark": "T5 兼容性验证"
                                }
                                """.formatted(deviceIdsJson, startTime, endTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceCount").value(devices.size()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("reservation").path("id").asText();
    }

    private void approveReservation(String reservationId, User deviceAdmin) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "整单审批通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    private void checkInReservation(String reservationId, User user, LocalDateTime checkInTime) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(checkInTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN"));
    }

    private void confirmBorrow(String reservationId, User deviceAdmin, LocalDateTime borrowTime) throws Exception {
        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "%s",
                                  "borrowCheckStatus": "为后续分叉测试准备借出",
                                  "remark": "先整单借出"
                                }
                                """.formatted(borrowTime)))
                .andExpect(status().isOk());
    }

    private List<BorrowRow> loadBorrowRowsByReservationId(String reservationId) {
        return jdbcTemplate.query(
                "SELECT id, device_id, status FROM borrow_record WHERE reservation_id = ? ORDER BY created_at ASC, id ASC",
                this::mapBorrowRow,
                reservationId);
    }

    private String loadBorrowStatus(String borrowRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM borrow_record WHERE id = ?",
                String.class,
                borrowRecordId);
    }

    private String loadDeviceStatus(String deviceId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM device WHERE id = ?",
                String.class,
                deviceId);
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
        category.setName("预约兼容测试分类-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 8));
        category.setDefaultApprovalMode(approvalMode);
        category.setSortOrder(1);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("预约兼容测试设备-" + UuidUtil.randomUuid().substring(0, 8));
        device.setDeviceNumber("RWF-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约兼容性测试设备");
        device.setLocation("Reservation-Lab");
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
