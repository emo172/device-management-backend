package com.jhun.backend.integration.borrow;

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
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * 借还控制器集成测试。
 * <p>
 * 用于锁定借用确认、归还确认、记录查询与设备状态联动，确保 borrow_record、device 与 device_status_log 三者保持一致。
 */
@SpringBootTest
@ActiveProfiles("test")
class BorrowControllerIntegrationTest {

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
    private DeviceStatusLogMapper deviceStatusLogMapper;
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
     * 验证设备管理员确认借用后，会生成唯一借还记录并把设备状态切换到借出中。
     */
    @Test
    void shouldConfirmBorrowAndCreateBorrowRecord() throws Exception {
        User user = createUser("brw-u-1", "borrow-user-1@example.com", "USER");
        User deviceAdmin = createUser("brw-da-1", "borrow-device-admin-1@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-22T09:00:00", "2026-03-22T11:00:00", "2026-03-22T09:20:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-22T09:30:00",
                                  "borrowCheckStatus": "机身完好，配件齐全",
                                  "remark": "现场借出"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("BORROWED"));

        BorrowRecordRow borrowRecord = loadBorrowRecordByReservationId(reservationId);
        Device refreshedDevice = deviceMapper.selectById(device.getId());

        org.junit.jupiter.api.Assertions.assertNotNull(borrowRecord);
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", borrowRecord.status());
        org.junit.jupiter.api.Assertions.assertEquals(deviceAdmin.getId(), borrowRecord.operatorId());
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED", refreshedDevice.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("借用确认", refreshedDevice.getStatusChangeReason());
        org.junit.jupiter.api.Assertions.assertEquals("BORROWED",
                deviceStatusLogMapper.findByDeviceId(device.getId()).getFirst().getNewStatus());
    }

    /**
     * 验证 SYSTEM_ADMIN 不能参与借用确认，避免越权破坏 DEVICE_ADMIN 独占的借还职责边界。
     */
    @Test
    void shouldRejectBorrowConfirmationBySystemAdmin() throws Exception {
        User user = createUser("brw-u-2", "borrow-user-2@example.com", "USER");
        User deviceAdmin = createUser("brw-da-2", "borrow-device-admin-2@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("brw-sa-1", "borrow-system-admin-1@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-22T13:00:00", "2026-03-22T15:00:00", "2026-03-22T13:10:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-22T13:20:00",
                                  "borrowCheckStatus": "系统管理员尝试越权",
                                  "remark": "不应成功"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private String createCheckedInReservation(
            User user,
            User deviceAdmin,
            Device device,
            String startTime,
            String endTime,
            String checkInTime) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "借还测试预约",
                                  "remark": "借还测试预约备注"
                                }
                                """.formatted(device.getId(), startTime, endTime)))
                .andExpect(status().isOk())
                .andReturn();
        String reservationId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "审批通过，允许借用"
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
                                """.formatted(checkInTime)))
                .andExpect(status().isOk());

        return reservationId;
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

    private Device createDevice(String approvalMode) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("借还测试分类-" + approvalMode + "-" + UuidUtil.randomUuid().substring(0, 8));
        category.setDefaultApprovalMode(approvalMode);
        category.setSortOrder(1);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("借还测试设备-" + UuidUtil.randomUuid().substring(0, 8));
        device.setDeviceNumber("BR-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("借还测试设备");
        device.setLocation("Borrow-Lab");
        deviceMapper.insert(device);
        return device;
    }

    private String bearer(User user, String roleCode) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), roleCode);
    }

    private BorrowRecordRow loadBorrowRecordByReservationId(String reservationId) {
        return jdbcTemplate.query(
                """
                        SELECT id, status, operator_id, return_operator_id
                        FROM borrow_record
                        WHERE reservation_id = ?
                        """,
                (rs, rowNum) -> mapBorrowRecordRow(rs),
                reservationId).stream().findFirst().orElse(null);
    }

    private BorrowRecordRow mapBorrowRecordRow(ResultSet resultSet) throws SQLException {
        return new BorrowRecordRow(
                resultSet.getString("id"),
                resultSet.getString("status"),
                resultSet.getString("operator_id"),
                resultSet.getString("return_operator_id"));
    }

    private record BorrowRecordRow(String id, String status, String operatorId, String returnOperatorId) {
    }
}
