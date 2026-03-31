package com.jhun.backend.integration.reservation;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 预约控制器集成测试。
 * <p>
 * 用于覆盖预约创建、审批、签到、列表、详情与取消规则，确保预约主链路在 SQL 新口径下既满足状态机约束，
 * 也满足“普通用户仅本人可见、管理角色可按管理视角查看”的接口契约。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

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
     * 验证 DEVICE_ONLY 模式创建预约后进入待设备审批状态。
     */
    @Test
    void shouldCreateReservationWithDeviceApprovalStatus() throws Exception {
        User user = createUser("reserve-user-1", "reserve-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(5, 9, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "课程演示",
                                  "remark": "第一条预约"
                                }
                                """.formatted(device.getId(), formatTime(startTime), formatTime(endTime))))
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
        LocalDateTime startTime = futureTime(5, 11, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "课程录制",
                                  "remark": "双审批预约"
                                }
                                """.formatted(device.getId(), formatTime(startTime), formatTime(endTime))))
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
     * 验证一审响应直接返回审批流所需的完整上下文字段，
     * 防止前端在待审批页或详情页只能拿到状态码、还要额外凭空拼设备名和审批人信息。
     */
    @Test
    void shouldReturnWorkflowContextAfterDeviceApproval() throws Exception {
        User user = createUser("reserve-user-2b", "reserve-user-2b@example.com", "USER");
        User deviceAdmin = createUser("rsv-da-2b", "reserve-device-admin-2b@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_THEN_SYSTEM");
        LocalDateTime startTime = futureTime(5, 15, 0);
        LocalDateTime endTime = startTime.plusHours(1);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(endTime), "审批上下文", "一审回包字段");

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
                .andExpect(jsonPath("$.data.status").value("PENDING_SYSTEM_APPROVAL"))
                .andExpect(jsonPath("$.data.reservationMode").value("SELF"))
                .andExpect(jsonPath("$.data.approvalModeSnapshot").value("DEVICE_THEN_SYSTEM"))
                .andExpect(jsonPath("$.data.signStatus").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.data.userName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.deviceNumber").value(device.getDeviceNumber()))
                .andExpect(jsonPath("$.data.deviceApproverId").value(deviceAdmin.getId()))
                .andExpect(jsonPath("$.data.deviceApproverName").value(deviceAdmin.getUsername()))
                .andExpect(jsonPath("$.data.deviceApprovedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.startTime").value(formatTime(startTime)))
                .andExpect(jsonPath("$.data.endTime").value(formatTime(endTime)));
    }

    /**
     * 验证同一账号不能完成双审两步，保护应用层双审隔离规则。
     */
    @Test
    void shouldRejectSecondApprovalBySameAccount() throws Exception {
        User user = createUser("reserve-user-3", "reserve-user-3@example.com", "USER");
        User dualRoleUser = createUser("reserve-dual-role", "reserve-dual-role@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_THEN_SYSTEM");
        LocalDateTime startTime = futureTime(5, 13, 0);
        LocalDateTime endTime = startTime.plusHours(1);

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "实验演示",
                                  "remark": "双审隔离测试"
                                }
                                """.formatted(device.getId(), formatTime(startTime), formatTime(endTime))))
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

    /**
     * 验证 `DEVICE_THEN_SYSTEM` 双审批链路可以完整闭环到 `APPROVED`，
     * 且二审成功响应继续携带前端详情页/待审批页需要的 workflow context，避免页面在二审成功后再次拼装审批人和设备信息。
     */
    @Test
    void shouldApproveReservationAfterSecondApprovalAndReturnWorkflowContext() throws Exception {
        User user = createUser("rsv-u-2nd-ok", "reserve-user-second-ok@example.com", "USER");
        User deviceAdmin = createUser("rsv-da-2ndok", "reserve-device-second-ok@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("rsv-sa-2ndok", "reserve-system-second-ok@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_THEN_SYSTEM");
        LocalDateTime startTime = futureTime(7, 10, 0);
        LocalDateTime endTime = startTime.plusHours(2);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(endTime), "双审批闭环", "验证二审完成回包");

        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "设备侧通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_SYSTEM_APPROVAL"));

        mockMvc.perform(post("/api/reservations/{id}/system-audit", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "系统侧通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvalModeSnapshot").value("DEVICE_THEN_SYSTEM"))
                .andExpect(jsonPath("$.data.signStatus").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.data.userName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.createdByName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.deviceNumber").value(device.getDeviceNumber()))
                .andExpect(jsonPath("$.data.startTime").value(formatTime(startTime)))
                .andExpect(jsonPath("$.data.endTime").value(formatTime(endTime)))
                .andExpect(jsonPath("$.data.deviceApproverId").value(deviceAdmin.getId()))
                .andExpect(jsonPath("$.data.deviceApproverName").value(deviceAdmin.getUsername()))
                .andExpect(jsonPath("$.data.deviceApprovedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.systemApproverId").value(systemAdmin.getId()))
                .andExpect(jsonPath("$.data.systemApproverName").value(systemAdmin.getUsername()))
                .andExpect(jsonPath("$.data.systemApprovedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.systemApprovalRemark").value("系统侧通过"));
    }

    /**
     * 验证用户在签到窗口内可以签到，保护“开始前 30 分钟到开始后 30 分钟为正常签到”的规则。
     */
    @Test
    void shouldCheckInWithinWindow() throws Exception {
        User user = createUser("rsv-ci-u1", "reserve-user-checkin-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-ci-da1", "reserve-device-admin-checkin-1@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(6, 9, 0);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(startTime.plusMinutes(20)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN"));
    }

    /**
     * 验证开始后 30~60 分钟签到会统一落成 `CHECKED_IN_TIMEOUT`，
     * 且签到响应要直接携带详情页继续展示所需的设备、预约人与签到时间字段。
     */
    @Test
    void shouldReturnLateCheckInWorkflowContext() throws Exception {
        User user = createUser("rsv-ci-u1b", "reserve-user-checkin-1b@example.com", "USER");
        User deviceAdmin = createUser("rsv-ci-da1b", "reserve-device-admin-checkin-1b@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(6, 15, 0);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));
        LocalDateTime checkInTime = startTime.plusMinutes(45);

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(checkInTime))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN_TIMEOUT"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reservationMode").value("SELF"))
                .andExpect(jsonPath("$.data.approvalModeSnapshot").value("DEVICE_ONLY"))
                .andExpect(jsonPath("$.data.userName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.checkedInAt").value(formatTime(checkInTime)))
                .andExpect(jsonPath("$.data.startTime").value(formatTime(startTime)));
    }

    /**
     * 验证开始后超过 60 分钟签到会被拒绝，保护“超过 60 分钟未签到则预约过期”的规则。
     */
    @Test
    void shouldRejectCheckInAfterTimeoutWindow() throws Exception {
        User user = createUser("rsv-ci-u2", "reserve-user-checkin-2@example.com", "USER");
        User deviceAdmin = createUser("rsv-ci-da2", "reserve-device-admin-checkin-2@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(6, 11, 0);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(startTime.plusMinutes(70)))))
                .andExpect(status().isBadRequest());

        /**
         * 签到超时虽然返回 400，但后端仍必须把预约推进到 EXPIRED，
         * 否则前端只看到“超时”提示，数据库里却还保留 APPROVED，会导致后续借还和列表视图继续把它当成有效预约。
         */
        var reservation = reservationMapper.selectById(reservationId);
        org.assertj.core.api.Assertions.assertThat(reservation.getStatus()).isEqualTo("EXPIRED");
        org.assertj.core.api.Assertions.assertThat(reservation.getSignStatus()).isEqualTo("NOT_CHECKED_IN");
    }

    /**
     * 验证设备管理员可以处理待人工预约，保护 PENDING_MANUAL 人工闭环能力。
     */
    @Test
    void shouldAllowDeviceAdminManualProcess() throws Exception {
        User user = createUser("rsv-man-u1", "reserve-user-manual-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-man-da1", "reserve-device-admin-manual-1@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(6, 13, 0);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(startTime.plusMinutes(20)))))
                .andExpect(status().isOk());

        var reservation = reservationMapper.selectById(reservationId);
        reservation.setStatus("PENDING_MANUAL");
        reservationMapper.updateById(reservation);

        mockMvc.perform(put("/api/reservations/{id}/manual-process", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "人工复核后保持有效"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /**
     * 验证普通用户查询预约列表时只能看到本人预约，防止列表页成为越权浏览他人预约的入口。
     */
    @Test
    void shouldListOnlyOwnReservationsForUser() throws Exception {
        User currentUser = createUser("rsv-list-u1", "reserve-list-user-1@example.com", "USER");
        User anotherUser = createUser("rsv-list-u2", "reserve-list-user-2@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime firstStart = futureTime(10, 9, 0);

        createReservation(currentUser, device, formatTime(firstStart), formatTime(firstStart.plusHours(1)), "本人预约", "列表可见性");
        createReservation(anotherUser, device, formatTime(firstStart.plusMinutes(90)), formatTime(firstStart.plusMinutes(150)), "他人预约", "列表隔离");

        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(currentUser, "USER"))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(currentUser.getId()))
                .andExpect(jsonPath("$.data.records[0].deviceName").value(device.getName()));
    }

    /**
     * 验证普通用户在固定 `page=1,size=5` 下的预约列表基线。
     * <p>
     * 这里显式把分页参数钉死为前端默认值，避免测试只在 `size=10` 时通过、而真实前端请求 `size=5` 时才暴露分页或排序问题。
     */
    @Test
    void shouldListFirstFiveOwnReservationsForUserWithFixedPaging() throws Exception {
        User currentUser = createUser("rsv-page5-u1", "reserve-page5-user-1@example.com", "USER");
        User anotherUser = createUser("rsv-page5-u2", "reserve-page5-user-2@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime firstStart = futureTime(25, 9, 0);

        createReservation(currentUser, device, formatTime(firstStart), formatTime(firstStart.plusHours(1)), "本人分页预约-1", "page=1,size=5");
        createReservation(currentUser, device, formatTime(firstStart.plusMinutes(90)), formatTime(firstStart.plusMinutes(150)), "本人分页预约-2", "page=1,size=5");
        createReservation(currentUser, device, formatTime(firstStart.plusMinutes(180)), formatTime(firstStart.plusMinutes(240)), "本人分页预约-3", "page=1,size=5");
        createReservation(currentUser, device, formatTime(firstStart.plusMinutes(270)), formatTime(firstStart.plusMinutes(330)), "本人分页预约-4", "page=1,size=5");
        createReservation(currentUser, device, formatTime(firstStart.plusMinutes(360)), formatTime(firstStart.plusMinutes(420)), "本人分页预约-5", "page=1,size=5");
        createReservation(currentUser, device, formatTime(firstStart.plusMinutes(450)), formatTime(firstStart.plusMinutes(510)), "本人分页预约-6", "page=1,size=5");
        createReservation(anotherUser, device, formatTime(firstStart.plusMinutes(540)), formatTime(firstStart.plusMinutes(600)), "他人分页预约", "不应出现在本人视角");

        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(currentUser, "USER"))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.records.length()").value(5))
                .andExpect(jsonPath("$.data.records[0].purpose").value("本人分页预约-6"))
                .andExpect(jsonPath("$.data.records[1].purpose").value("本人分页预约-5"))
                .andExpect(jsonPath("$.data.records[2].purpose").value("本人分页预约-4"))
                .andExpect(jsonPath("$.data.records[3].purpose").value("本人分页预约-3"))
                .andExpect(jsonPath("$.data.records[4].purpose").value("本人分页预约-2"));
    }

    /**
     * 验证普通用户不能查看他人预约详情，防止详情接口绕过列表页的本人过滤规则。
     */
    @Test
    void shouldRejectReservationDetailOfAnotherUserForNormalUser() throws Exception {
        User owner = createUser("rsv-detail-u1", "reserve-detail-user-1@example.com", "USER");
        User anotherUser = createUser("rsv-detail-u2", "reserve-detail-user-2@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(11, 9, 0);
        String reservationId = createReservation(owner, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "本人详情", "详情隔离");

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(anotherUser, "USER")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证只有普通用户可以走本人预约创建入口，防止设备管理员或系统管理员绕过代预约边界直接为自己下单。
     */
    @Test
    void shouldRejectSelfReservationCreationForNonUserRoles() throws Exception {
        User deviceAdmin = createUser("rsv-create-da1", "reserve-create-da1@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("rsv-create-sa1", "reserve-create-sa1@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(11, 15, 0);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "设备管理员本人预约",
                                  "remark": "应被拒绝"
                                }
                                """.formatted(device.getId(), formatTime(startTime), formatTime(startTime.plusHours(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有普通用户可以创建本人预约"));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "%s",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "purpose": "系统管理员本人预约",
                                  "remark": "应被拒绝"
                                }
                                """.formatted(device.getId(), formatTime(startTime.plusMinutes(90)), formatTime(startTime.plusMinutes(150)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有普通用户可以创建本人预约"));
    }

    /**
     * 验证管理角色可以查询预约列表，确保后台预约页能看到管理视角的全量记录。
     */
    @Test
    void shouldAllowManagerToListReservations() throws Exception {
        User userOne = createUser("rsv-mgr-list-u1", "reserve-manager-list-user-1@example.com", "USER");
        User userTwo = createUser("rsv-mgr-list-u2", "reserve-manager-list-user-2@example.com", "USER");
        User systemAdmin = createUser("rsv-mgr-list-admin", "reserve-manager-list-admin@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime firstStart = futureTime(12, 9, 0);

        createReservation(userOne, device, formatTime(firstStart), formatTime(firstStart.plusHours(1)), "用户一预约", "管理列表");
        createReservation(userTwo, device, formatTime(firstStart.plusMinutes(90)), formatTime(firstStart.plusMinutes(150)), "用户二预约", "管理列表");

        /*
         * 该集成测试与其他预约/逾期用例共享测试库上下文，管理视角读取的又是全量预约；
         * 因此这里把页大小显式放大，确保断言验证的是“能看到本次新建记录”，而不是首页 10 条恰好如何排序。
         */
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.records[*].userName").value(hasItems(userOne.getUsername(), userTwo.getUsername())));
    }

    /**
     * 验证系统管理员在固定 `page=1,size=5` 下仍能拿到管理视角首页数据。
     * <p>
     * 这里用 6 条最新样本把首页裁成 5 条，确保管理视角在真实分页参数下既能返回 200，也能维持稳定排序。
     */
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void shouldListFirstFiveReservationsForSystemAdminWithFixedPaging() throws Exception {
        User userOne = createUser("rsv-mgr-page5-u1", "reserve-manager-page5-user-1@example.com", "USER");
        User userTwo = createUser("rsv-mgr-page5-u2", "reserve-manager-page5-user-2@example.com", "USER");
        User systemAdmin = createUser("rsv-mgr-page5-admin", "reserve-manager-page5-admin@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime firstStart = futureTime(26, 9, 0);

        createReservation(userOne, device, formatTime(firstStart), formatTime(firstStart.plusHours(1)), "管理分页预约-1", "page=1,size=5");
        createReservation(userTwo, device, formatTime(firstStart.plusMinutes(90)), formatTime(firstStart.plusMinutes(150)), "管理分页预约-2", "page=1,size=5");
        createReservation(userOne, device, formatTime(firstStart.plusMinutes(180)), formatTime(firstStart.plusMinutes(240)), "管理分页预约-3", "page=1,size=5");
        createReservation(userTwo, device, formatTime(firstStart.plusMinutes(270)), formatTime(firstStart.plusMinutes(330)), "管理分页预约-4", "page=1,size=5");
        createReservation(userOne, device, formatTime(firstStart.plusMinutes(360)), formatTime(firstStart.plusMinutes(420)), "管理分页预约-5", "page=1,size=5");
        createReservation(userTwo, device, formatTime(firstStart.plusMinutes(450)), formatTime(firstStart.plusMinutes(510)), "管理分页预约-6", "page=1,size=5");

        /*
         * 这条回归必须固定断言首页 5 条和 total=6；如果继续复用共享 Spring 上下文，其他用例残留预约会挤占首页排序，
         * 让这里测成“测试库历史数据谁更靠前”而不是“page=1,size=5 的管理分页契约”。
         * 结合 application-test.yml 里每个测试上下文都会拿到独立随机 H2 库名，这里用方法级独立上下文最贴合该回归目标。
         */
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.records.length()").value(5))
                .andExpect(jsonPath("$.data.records[0].purpose").value("管理分页预约-6"))
                .andExpect(jsonPath("$.data.records[1].purpose").value("管理分页预约-5"))
                .andExpect(jsonPath("$.data.records[2].purpose").value("管理分页预约-4"))
                .andExpect(jsonPath("$.data.records[3].purpose").value("管理分页预约-3"))
                .andExpect(jsonPath("$.data.records[4].purpose").value("管理分页预约-2"))
                .andExpect(jsonPath("$.data.records[*].userName").value(hasItems(userOne.getUsername(), userTwo.getUsername())));
    }

    /**
     * 验证预约列表只对白名单三角色开放，防止任意伪造角色被当成管理视角读取全量预约。
     */
    @Test
    void shouldRejectReservationListForUnsupportedRole() throws Exception {
        User user = createUser("rsv-role-list-u1", "reserve-role-list-user-1@example.com", "USER");

        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", bearer(user, "AUDITOR"))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证如果只是预约关联数据漂移到脏设备引用，固定分页列表会落成 400 业务异常。
     * <p>
     * 这条用例的目的不是制造 dev 500，而是明确区分“后续装配发现坏关联”与“SQL/schema 级故障”两类入口，
     * 避免把脏数据触发的业务异常误判成当前开发环境里的 500。
     */
    @Test
    void shouldReturnBadRequestForAssociationDriftOnFixedPaging() throws Exception {
        User user = createUser("rsv-drift-u1", "reserve-drift-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(27, 9, 0);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "脏关联预约", "设备引用漂移");

        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.update("UPDATE reservation SET device_id = ? WHERE id = ?", "ghost-device-id", reservationId);

            mockMvc.perform(get("/api/reservations")
                            .header("Authorization", bearer(user, "USER"))
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("设备不存在"));
        } finally {
            jdbcTemplate.update("UPDATE reservation SET device_id = ? WHERE id = ?", device.getId(), reservationId);
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /**
     * 验证管理角色可以查看预约详情成功路径，确保后台详情页可直接获取设备、审批与创建人字段。
     */
    @Test
    void shouldAllowManagerToGetReservationDetail() throws Exception {
        User user = createUser("rsv-mgr-detail-u1", "reserve-manager-detail-user-1@example.com", "USER");
        User systemAdmin = createUser("rsv-mgr-detail-admin", "reserve-manager-detail-admin@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(12, 13, 0);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "管理详情预约", "详情字段");

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservationId))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.userName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.createdBy").value(user.getId()))
                .andExpect(jsonPath("$.data.createdByName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.deviceId").value(device.getId()))
                .andExpect(jsonPath("$.data.deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.deviceNumber").value(device.getDeviceNumber()))
                .andExpect(jsonPath("$.data.deviceStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.approvalModeSnapshot").value("DEVICE_ONLY"));
    }

    /**
     * 验证普通用户也能查看本人预约详情成功路径，证明详情字段足以支撑前台详情页联调。
     */
    @Test
    void shouldAllowUserToGetOwnReservationDetail() throws Exception {
        User user = createUser("rsv-own-detail-u1", "reserve-own-detail-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(12, 15, 0);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "本人详情预约", "详情字段联调");

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservationId))
                .andExpect(jsonPath("$.data.userName").value(user.getUsername()))
                .andExpect(jsonPath("$.data.deviceName").value(device.getName()))
                .andExpect(jsonPath("$.data.purpose").value("本人详情预约"))
                .andExpect(jsonPath("$.data.remark").value("详情字段联调"))
                .andExpect(jsonPath("$.data.status").value("PENDING_DEVICE_APPROVAL"))
                .andExpect(jsonPath("$.data.signStatus").value("NOT_CHECKED_IN"));
    }

    /**
     * 验证预约详情只对白名单三角色开放，防止未知角色被误判成管理角色查看任意详情。
     */
    @Test
    void shouldRejectReservationDetailForUnsupportedRole() throws Exception {
        User user = createUser("rsv-role-detail-u1", "reserve-role-detail-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = futureTime(12, 17, 0);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "角色白名单详情", "非法角色");

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .header("Authorization", bearer(user, "AUDITOR")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证普通用户在开始前超过 24 小时时可以自行取消预约，保护用户自助取消窗口规则。
     */
    @Test
    void shouldAllowUserCancelReservationBeforeTwentyFourHours() throws Exception {
        User user = createUser("rsv-cancel-u1", "reserve-cancel-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = alignedNow().plusDays(3).withHour(10).withMinute(0);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "可取消预约", "超过 24 小时");

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "计划调整"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("计划调整"));
    }

    /**
     * 验证普通用户在开始前 24 小时内不能自行取消预约，防止用户绕过“需管理员处理”的线下协同规则。
     */
    @Test
    void shouldRejectUserCancelReservationWithinTwentyFourHours() throws Exception {
        User user = createUser("rsv-cancel-u2", "reserve-cancel-user-2@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = alignedNow().plusHours(6);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "不可自取消预约", "24 小时内");

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "临时有事"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证管理角色可以处理开始前 24 小时内的取消，保护“24 小时内需管理员处理”的业务规则。
     */
    @Test
    void shouldAllowManagerCancelReservationWithinTwentyFourHours() throws Exception {
        User user = createUser("rsv-cancel-mgr-u1", "reserve-cancel-manager-user-1@example.com", "USER");
        User systemAdmin = createUser("rsv-cancel-mgr-admin", "reserve-cancel-manager-admin@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = alignedNow().plusHours(5);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "管理员取消预约", "24 小时内管理员处理");

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "后台协助取消"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("后台协助取消"));
    }

    /**
     * 验证已完成签到的预约即使尚未到开始时间，也不能再被管理员取消，
     * 防止签到后的预约从借用确认链路被直接回滚成取消状态。
     */
    @Test
    void shouldRejectCancelReservationAfterCheckInEvenBeforeStart() throws Exception {
        User user = createUser("rsv-cancel-ci-u1", "reserve-cancel-checkin-user-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-cancel-ci-da1", "reserve-cancel-checkin-da1@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("rsv-cancel-ci-sa1", "reserve-cancel-checkin-sa1@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime checkInTime = alignedNow();
        LocalDateTime startTime = checkInTime.plusMinutes(20);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(checkInTime))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signStatus").value("CHECKED_IN"));

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "签到后尝试取消"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("已签到预约不可取消"));
    }

    /**
     * 验证原子取消 SQL 本身也会拦住已签到预约，防止后续只保住服务层分支测试、
     * 却把 Mapper XML 中的签到条件误删后重新引入数据库覆盖风险。
     */
    @Test
    void shouldNotCancelCheckedInReservationThroughAtomicSql() throws Exception {
        User user = createUser("rsv-sql-ci-u1", "reserve-sql-checkin-user-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-sql-ci-da1", "reserve-sql-checkin-da1@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime checkInTime = alignedNow();
        LocalDateTime startTime = checkInTime.plusMinutes(20);
        String reservationId = createApprovedReservation(user, deviceAdmin, device, formatTime(startTime), formatTime(startTime.plusHours(1)));

        mockMvc.perform(post("/api/reservations/{id}/check-in", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "checkInTime": "%s"
                                }
                                """.formatted(formatTime(checkInTime))))
                .andExpect(status().isOk());

        LocalDateTime now = alignedNow();
        int affectedRows = reservationMapper.cancelReservationSafely(
                reservationId,
                "SQL 守卫校验",
                now,
                now,
                now,
                List.of("PENDING_DEVICE_APPROVAL", "PENDING_SYSTEM_APPROVAL", "PENDING_MANUAL", "APPROVED"));

        var reservation = reservationMapper.selectById(reservationId);
        org.junit.jupiter.api.Assertions.assertEquals(0, affectedRows);
        org.junit.jupiter.api.Assertions.assertEquals("APPROVED", reservation.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("CHECKED_IN", reservation.getSignStatus());
    }

    /**
     * 验证预约开始后任何角色都不能取消，避免已进入执行窗口的预约被直接回滚。
     */
    @Test
    void shouldRejectCancelReservationAfterStartForAnyRole() throws Exception {
        User user = createUser("rsv-cancel-after-u1", "reserve-cancel-after-user-1@example.com", "USER");
        User deviceAdmin = createUser("rsv-cancel-after-da1", "reserve-cancel-after-da1@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("rsv-cancel-after-sa1", "reserve-cancel-after-sa1@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = alignedNow().minusHours(2);
        String reservationId = createReservation(user, device, formatTime(startTime), formatTime(startTime.plusHours(1)), "开始后不可取消", "已进入执行窗口");

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(user, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "用户尝试取消"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "设备管理员尝试取消"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "系统管理员尝试取消"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 用统一入口创建预约，避免每个测试重复拼接请求体时遗漏关键时间或用途字段。
     */
    private String createReservation(User user, Device device, String startTime, String endTime, String purpose, String remark)
            throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/reservations")
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
                                """.formatted(device.getId(), startTime, endTime, purpose, remark)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createApprovedReservation(
            User user,
            User deviceAdmin,
            Device device,
            String startTime,
            String endTime) throws Exception {
        String reservationId = createReservation(user, device, startTime, endTime, "签到测试预约", "用于 Task10");

        mockMvc.perform(post("/api/reservations/{id}/audit", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "remark": "审批通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        return reservationId;
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

    /**
     * 统一把测试时间锚定到“当前整分钟”，避免固定日期随着真实日历推进而自然腐化。
     */
    private LocalDateTime alignedNow() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * 生成未来某天固定时分的测试时间，保证预约时间既可读又不依赖具体年份日期。
     */
    private LocalDateTime futureTime(int plusDays, int hour, int minute) {
        return alignedNow().plusDays(plusDays).withHour(hour).withMinute(minute);
    }

    /**
     * 统一输出 ISO 本地时间字符串，避免每个测试各自处理格式导致时间精度不一致。
     */
    private String formatTime(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS).format(ISO_SECOND_FORMATTER);
    }
}
