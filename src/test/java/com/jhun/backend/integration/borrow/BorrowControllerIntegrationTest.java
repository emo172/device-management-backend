package com.jhun.backend.integration.borrow;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.dto.borrow.ConfirmBorrowRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.DeviceStatusLog;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.BorrowService;
import com.jhun.backend.util.UuidUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
    @MockitoSpyBean
    private DeviceStatusLogMapper deviceStatusLogMapperSpy;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private BorrowService borrowService;
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

    /**
     * 验证预约尚未审批通过时禁止确认借用，避免管理员绕过预约审批链直接占用设备。
     */
    @Test
    void shouldRejectBorrowConfirmationWhenReservationNotApproved() throws Exception {
        User user = createUser("brw-u-5", "borrow-user-5@example.com", "USER");
        User deviceAdmin = createUser("brw-da-5", "borrow-device-admin-5@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createPendingReservation(user, device,
                "2026-03-25T09:00:00", "2026-03-25T11:00:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-25T09:10:00",
                                  "borrowCheckStatus": "试图跳过审批",
                                  "remark": "不应成功"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证预约虽已审批通过但未签到时仍禁止确认借用，保护“签到是借用前置条件”的业务规则。
     */
    @Test
    void shouldRejectBorrowConfirmationWhenSignStatusInvalid() throws Exception {
        User user = createUser("brw-u-6", "borrow-user-6@example.com", "USER");
        User deviceAdmin = createUser("brw-da-6", "borrow-device-admin-6@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createApprovedReservationWithoutCheckIn(user, deviceAdmin, device,
                "2026-03-25T13:00:00", "2026-03-25T15:00:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-25T13:20:00",
                                  "borrowCheckStatus": "用户未签到",
                                  "remark": "不应成功"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证同一预约重复确认借用会被拒绝，保护 SQL 唯一约束与业务幂等边界不会被第二次请求破坏。
     */
    @Test
    void shouldRejectDuplicateBorrowConfirmationForSameReservation() throws Exception {
        User user = createUser("brw-u-7", "borrow-user-7@example.com", "USER");
        User deviceAdmin = createUser("brw-da-7", "borrow-device-admin-7@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-26T09:00:00", "2026-03-26T10:30:00", "2026-03-26T09:05:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-26T09:10:00",
                                  "borrowCheckStatus": "首次借出",
                                  "remark": "第一次确认"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-26T09:15:00",
                                  "borrowCheckStatus": "重复借出",
                                  "remark": "第二次确认"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证并发重复确认借用时只能有一个成功，保护唯一借还记录、设备状态和状态日志不会被双写。
     */
    @Test
    void shouldAllowOnlyOneSuccessWhenConfirmBorrowConcurrently() throws Exception {
        User user = createUser("brw-u-9", "borrow-user-9@example.com", "USER");
        User deviceAdmin = createUser("brw-da-9", "borrow-device-admin-9@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-27T09:00:00", "2026-03-27T11:00:00", "2026-03-27T09:05:00");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> task = () -> runConcurrentBorrowAttempt(reservationId, deviceAdmin.getId(), ready, start);
            Future<Boolean> first = executorService.submit(task);
            Future<Boolean> second = executorService.submit(task);
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successCount = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            org.junit.jupiter.api.Assertions.assertEquals(1, successCount);
            org.junit.jupiter.api.Assertions.assertEquals(1L, countBorrowRecordsByReservationId(reservationId));
            org.junit.jupiter.api.Assertions.assertEquals(1L, countDeviceStatusLogsByDeviceId(device.getId()));
            org.junit.jupiter.api.Assertions.assertEquals("BORROWED", deviceMapper.selectById(device.getId()).getStatus());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 验证借用确认在写入 borrow_record 后如果设备状态日志写入失败，会整体事务回滚。
     * <p>
     * 该场景用于证明借用确认不是“前半成功、后半失败”的半事务：
     * 即使 borrow_record 已经尝试插入，只要后续联动步骤异常，最终也不会残留借还记录或错误的设备状态。
     */
    @Test
    void shouldRollbackBorrowConfirmationWhenDeviceStatusLogInsertFails() throws Exception {
        User user = createUser("brw-u-11", "borrow-user-11@example.com", "USER");
        User deviceAdmin = createUser("brw-da-11", "borrow-device-admin-11@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-28T09:00:00", "2026-03-28T11:00:00", "2026-03-28T09:05:00");

        doThrow(new RuntimeException("模拟设备状态日志写入失败"))
                .when(deviceStatusLogMapperSpy)
                .insert(any(DeviceStatusLog.class));

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-28T09:10:00",
                                  "borrowCheckStatus": "日志失败回滚测试",
                                  "remark": "事务回滚验证"
                                }
                                """))
                .andExpect(status().isInternalServerError());

        org.junit.jupiter.api.Assertions.assertEquals(0L, countBorrowRecordsByReservationId(reservationId));
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", deviceMapper.selectById(device.getId()).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(0L, countDeviceStatusLogsByDeviceId(device.getId()));
    }

    /**
     * 验证设备管理员确认归还后，借还记录会闭环为已归还，且设备才允许回到 AVAILABLE。
     */
    @Test
    void shouldConfirmReturnAndRestoreDeviceAvailable() throws Exception {
        User user = createUser("brw-u-3", "borrow-user-3@example.com", "USER");
        User deviceAdmin = createUser("brw-da-3", "borrow-device-admin-3@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-23T09:00:00", "2026-03-23T11:00:00", "2026-03-23T09:15:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-23T09:25:00",
                                  "borrowCheckStatus": "借出前检查正常",
                                  "remark": "先借出"
                                }
                                """))
                .andExpect(status().isOk());

        BorrowRecordRow borrowRecord = loadBorrowRecordByReservationId(reservationId);

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowRecord.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-03-23T10:50:00",
                                  "returnCheckStatus": "设备归还完好",
                                  "remark": "按时归还"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(borrowRecord.id()))
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        BorrowRecordRow refreshedBorrowRecord = loadBorrowRecordByReservationId(reservationId);
        Device refreshedDevice = deviceMapper.selectById(device.getId());

        org.junit.jupiter.api.Assertions.assertEquals("RETURNED", refreshedBorrowRecord.status());
        org.junit.jupiter.api.Assertions.assertEquals(deviceAdmin.getId(), refreshedBorrowRecord.returnOperatorId());
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", refreshedDevice.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("归还确认", refreshedDevice.getStatusChangeReason());
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE",
                deviceStatusLogMapper.findByDeviceId(device.getId()).getFirst().getNewStatus());
    }

    /**
     * 验证逾期中的借还记录仍然可以通过正式归还流程闭环，防止 OVERDUE 记录被误当作不可归还的死状态。
     */
    @Test
    void shouldConfirmReturnSuccessfullyWhenBorrowRecordIsOverdue() throws Exception {
        User user = createUser("brw-u-ovr", "borrow-user-overdue-return@example.com", "USER");
        User deviceAdmin = createUser("brw-da-ovr", "borrow-device-admin-overdue-return@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-29T09:00:00", "2026-03-29T11:00:00", "2026-03-29T09:10:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-29T09:20:00",
                                  "borrowCheckStatus": "借出后将转逾期",
                                  "remark": "逾期归还测试"
                                }
                                """))
                .andExpect(status().isOk());

        BorrowRecordRow borrowRecord = loadBorrowRecordByReservationId(reservationId);
        jdbcTemplate.update(
                "UPDATE borrow_record SET status = 'OVERDUE', updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 3, 29, 11, 30, 0),
                borrowRecord.id());

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowRecord.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-03-29T11:40:00",
                                  "returnCheckStatus": "逾期后正常归还",
                                  "remark": "逾期记录已闭环"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        BorrowRecordRow refreshedBorrowRecord = loadBorrowRecordByReservationId(reservationId);
        Device refreshedDevice = deviceMapper.selectById(device.getId());
        org.junit.jupiter.api.Assertions.assertEquals("RETURNED", refreshedBorrowRecord.status());
        org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", refreshedDevice.getStatus());
    }

    /**
     * 验证 SYSTEM_ADMIN 不能确认归还，避免越权把设备直接从 BORROWED 恢复为 AVAILABLE。
     */
    @Test
    void shouldRejectReturnConfirmationBySystemAdmin() throws Exception {
        User user = createUser("brw-u-8", "borrow-user-8@example.com", "USER");
        User deviceAdmin = createUser("brw-da-8", "borrow-device-admin-8@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("brw-sa-8", "borrow-system-admin-8@example.com", "SYSTEM_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-26T13:00:00", "2026-03-26T15:00:00", "2026-03-26T13:10:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-26T13:15:00",
                                  "borrowCheckStatus": "已借出",
                                  "remark": "等待归还"
                                }
                                """))
                .andExpect(status().isOk());

        BorrowRecordRow borrowRecord = loadBorrowRecordByReservationId(reservationId);

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowRecord.id())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-03-26T14:50:00",
                                  "returnCheckStatus": "越权归还",
                                  "remark": "不应成功"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证重复归还不会二次成功，保护 borrow_record 和 device_status_log 不会在同一条记录上重复闭环。
     */
    @Test
    void shouldRejectDuplicateReturnConfirmation() throws Exception {
        User user = createUser("brw-u-10", "borrow-user-10@example.com", "USER");
        User deviceAdmin = createUser("brw-da-10", "borrow-device-admin-10@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-27T13:00:00", "2026-03-27T15:00:00", "2026-03-27T13:05:00");

        mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-27T13:10:00",
                                  "borrowCheckStatus": "首次借出",
                                  "remark": "准备归还"
                                }
                                """))
                .andExpect(status().isOk());

        BorrowRecordRow borrowRecord = loadBorrowRecordByReservationId(reservationId);

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowRecord.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-03-27T14:30:00",
                                  "returnCheckStatus": "首次归还",
                                  "remark": "第一次归还"
                                }
                                """))
                .andExpect(status().isOk());

        long statusLogCountAfterFirstReturn = countDeviceStatusLogsByDeviceId(device.getId());

        mockMvc.perform(post("/api/borrow-records/{borrowRecordId}/confirm-return", borrowRecord.id())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnTime": "2026-03-27T14:40:00",
                                  "returnCheckStatus": "重复归还",
                                  "remark": "第二次归还"
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(statusLogCountAfterFirstReturn, countDeviceStatusLogsByDeviceId(device.getId()));
    }

    /**
     * 验证借还列表与详情接口可以返回记录，用于支撑借还记录页与详情抽屉联调。
     */
    @Test
    void shouldListAndGetBorrowRecords() throws Exception {
        User user = createUser("brw-u-4", "borrow-user-4@example.com", "USER");
        User deviceAdmin = createUser("brw-da-4", "borrow-device-admin-4@example.com", "DEVICE_ADMIN");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = createCheckedInReservation(user, deviceAdmin, device,
                "2026-03-24T09:00:00", "2026-03-24T11:30:00", "2026-03-24T09:05:00");

        MvcResult confirmBorrowResult = mockMvc.perform(post("/api/borrow-records/{reservationId}/confirm-borrow", reservationId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowTime": "2026-03-24T09:10:00",
                                  "borrowCheckStatus": "借出前状态正常",
                                  "remark": "用于查询测试"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String borrowRecordId = objectMapper.readTree(confirmBorrowResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        mockMvc.perform(get("/api/borrow-records")
                        .header("Authorization", bearer(user, "USER"))
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "BORROWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(borrowRecordId));

        mockMvc.perform(get("/api/borrow-records/{id}", borrowRecordId)
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(borrowRecordId))
                .andExpect(jsonPath("$.data.deviceId").value(device.getId()))
                .andExpect(jsonPath("$.data.status").value("BORROWED"));
    }

    private String createCheckedInReservation(
            User user,
            User deviceAdmin,
            Device device,
            String startTime,
            String endTime,
            String checkInTime) throws Exception {
        String reservationId = createApprovedReservationWithoutCheckIn(user, deviceAdmin, device, startTime, endTime);

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

    private String createApprovedReservationWithoutCheckIn(
            User user,
            User deviceAdmin,
            Device device,
            String startTime,
            String endTime) throws Exception {
        String reservationId = createPendingReservation(user, device, startTime, endTime);

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

        return reservationId;
    }

    private String createPendingReservation(User user, Device device, String startTime, String endTime) throws Exception {
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
        return objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();
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

    private boolean runConcurrentBorrowAttempt(
            String reservationId,
            String operatorId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        org.junit.jupiter.api.Assertions.assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            borrowService.confirmBorrow(
                    reservationId,
                    operatorId,
                    "DEVICE_ADMIN",
                    new ConfirmBorrowRequest(LocalDateTime.of(2026, 3, 27, 9, 10), "并发借出校验", "并发借用测试"));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private long countBorrowRecordsByReservationId(String reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM borrow_record WHERE reservation_id = ?",
                Long.class,
                reservationId);
        return count == null ? 0L : count;
    }

    private long countDeviceStatusLogsByDeviceId(String deviceId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_status_log WHERE device_id = ?",
                Long.class,
                deviceId);
        return count == null ? 0L : count;
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
