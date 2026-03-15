package com.jhun.backend.integration.overdue;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.OverdueRecordMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.scheduler.overdue.OverdueAutoDetectProcessor;
import com.jhun.backend.scheduler.overdue.OverdueNotificationProcessor;
import com.jhun.backend.scheduler.overdue.OverdueRestrictionReleaseProcessor;
import com.jhun.backend.util.UuidUtil;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 逾期控制器与任务集成测试。
 * <p>
 * 该测试类覆盖 Task 12 约定的最小可用闭环：逾期识别、分段冻结、设备管理员处理、普通用户视角、
 * 逾期提醒通知与 RESTRICTED 自动释放，防止后续调整把逾期治理拆成“只有定时任务没有接口”或“只有接口没有状态联动”的半闭环。
 */
@SpringBootTest
@ActiveProfiles("test")
class OverdueControllerIntegrationTest {

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
    @Autowired
    private OverdueRecordMapper overdueRecordMapper;
    @Autowired
    private OverdueAutoDetectProcessor overdueAutoDetectProcessor;
    @Autowired
    private OverdueNotificationProcessor overdueNotificationProcessor;
    @Autowired
    private OverdueRestrictionReleaseProcessor overdueRestrictionReleaseProcessor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证逾期小于 4 小时时，会生成正式逾期记录并把借还状态切到 OVERDUE，但用户仍保持 NORMAL。
     */
    @Test
    void shouldDetectOverdueAndKeepUserNormalWhenLessThanFourHours() {
        User user = createUser("odu-normal", "od-user-normal@example.com", "USER");
        User deviceAdmin = createUser("odda-norm", "od-device-admin-normal@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 3, 30, 9, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 3, 30, 12, 0, 0));

        Map<String, Object> overdueRecord = loadOverdueRecordByBorrowRecordId(borrowRecordId);
        org.junit.jupiter.api.Assertions.assertNotNull(overdueRecord);
        org.junit.jupiter.api.Assertions.assertEquals("OVERDUE", loadBorrowStatus(borrowRecordId));
        org.junit.jupiter.api.Assertions.assertEquals(3, ((Number) overdueRecord.get("overdue_hours")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals("NORMAL", loadFreezeStatus(user.getId()));
    }

    /**
     * 验证逾期达到 4 小时后，用户会被限制为 RESTRICTED，避免仍以 NORMAL 状态继续发起新预约。
     */
    @Test
    void shouldRestrictUserWhenOverdueAtLeastFourHours() {
        User user = createUser("odu-rest", "od-user-restricted@example.com", "USER");
        User deviceAdmin = createUser("odda-rest", "od-device-admin-restricted@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 3, 31, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 3, 31, 12, 30, 0));

        org.junit.jupiter.api.Assertions.assertEquals("RESTRICTED", loadFreezeStatus(user.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(1L, countNotifications(user.getId(), "ACCOUNT_FREEZE_UNFREEZE"));
    }

    /**
     * 验证逾期达到 96 小时后，用户进入 FROZEN，且 C-07 自动释放任务不会把 FROZEN 误解冻。
     */
    @Test
    void shouldFreezeUserAtNinetySixHoursAndNotAutoRelease() {
        User user = createUser("odu-frozen", "od-user-frozen@example.com", "USER");
        User deviceAdmin = createUser("odda-frz", "od-device-admin-frozen@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 1, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 5, 12, 0, 0));
        jdbcTemplate.update(
                "UPDATE borrow_record SET status = 'RETURNED', return_time = ?, updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 4, 5, 12, 30, 0),
                LocalDateTime.of(2026, 4, 5, 12, 30, 0),
                borrowRecordId);

        overdueRestrictionReleaseProcessor.releaseAt(LocalDateTime.of(2026, 4, 6, 2, 0, 0));

        org.junit.jupiter.api.Assertions.assertEquals("FROZEN", loadFreezeStatus(user.getId()));
    }

    /**
     * 验证只有 DEVICE_ADMIN 可以处理逾期记录，SYSTEM_ADMIN 虽然能查看管理数据，但不能介入逾期处理职责。
     */
    @Test
    void shouldAllowDeviceAdminToProcessAndRejectSystemAdmin() throws Exception {
        User user = createUser("odu-proc", "od-user-process@example.com", "USER");
        User deviceAdmin = createUser("odda-proc", "od-device-admin-process@example.com", "DEVICE_ADMIN");
        User systemAdmin = createUser("odsa-proc", "od-system-admin-process@example.com", "SYSTEM_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 2, 8, 0, 0));
        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 2, 13, 0, 0));
        String overdueRecordId = loadOverdueRecordByBorrowRecordId(borrowRecordId).get("id").toString();

        mockMvc.perform(post("/api/overdue-records/{id}/process", overdueRecordId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingMethod": "COMPENSATION",
                                  "remark": "已登记赔偿",
                                  "compensationAmount": 88.50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.data.processorId").value(deviceAdmin.getId()));

        mockMvc.perform(post("/api/overdue-records/{id}/process", overdueRecordId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingMethod": "WARNING",
                                  "remark": "系统管理员不应有权处理",
                                  "compensationAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证普通用户列表接口只能看到本人逾期记录，避免已知逾期 ID 或筛选参数成为越权入口。
     */
    @Test
    void shouldOnlyListCurrentUsersOverdueRecords() throws Exception {
        User firstUser = createUser("odu-list1", "od-user-list-1@example.com", "USER");
        User secondUser = createUser("odu-list2", "od-user-list-2@example.com", "USER");
        User deviceAdmin = createUser("odda-list", "od-device-admin-list@example.com", "DEVICE_ADMIN");
        Device firstDevice = createDevice();
        Device secondDevice = createDevice();
        createBorrowRecord(firstUser, deviceAdmin, firstDevice, LocalDateTime.of(2026, 4, 3, 9, 0, 0));
        createBorrowRecord(secondUser, deviceAdmin, secondDevice, LocalDateTime.of(2026, 4, 3, 9, 30, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 3, 15, 0, 0));

        mockMvc.perform(get("/api/overdue-records")
                        .header("Authorization", bearer(firstUser, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(firstUser.getId()));
    }

    /**
     * 验证普通用户详情接口不能查看他人逾期记录，避免详情接口成为绕过列表过滤的越权入口。
     */
    @Test
    void shouldRejectOverdueDetailOfOtherUserForNormalUser() throws Exception {
        User firstUser = createUser("odu-det1", "od-user-detail-1@example.com", "USER");
        User secondUser = createUser("odu-det2", "od-user-detail-2@example.com", "USER");
        User deviceAdmin = createUser("odda-det", "od-device-admin-detail@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(secondUser, deviceAdmin, device, LocalDateTime.of(2026, 4, 3, 10, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 3, 15, 0, 0));
        String overdueRecordId = loadOverdueRecordByBorrowRecordId(borrowRecordId).get("id").toString();

        mockMvc.perform(get("/api/overdue-records/{id}", overdueRecordId)
                        .header("Authorization", bearer(firstUser, "USER")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证 C-06 会为未通知的逾期记录生成 OVERDUE_WARNING，并把 notification_sent 更新为已发送。
     */
    @Test
    void shouldSendOverdueWarningAndMarkNotificationSent() {
        User user = createUser("odu-notice", "od-user-notice@example.com", "USER");
        User deviceAdmin = createUser("odda-note", "od-device-admin-notice@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 4, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 4, 13, 0, 0));
        overdueNotificationProcessor.notifyAt(LocalDateTime.of(2026, 4, 4, 13, 30, 0));

        org.junit.jupiter.api.Assertions.assertEquals(1L, countNotifications(user.getId(), "OVERDUE_WARNING"));
        org.junit.jupiter.api.Assertions.assertEquals(1, loadNotificationSentFlag(borrowRecordId));
    }

    /**
     * 验证 C-06 的通知落库具备幂等性：即使任务重复执行，也不会重复写入 OVERDUE_WARNING。
     */
    @Test
    void shouldNotInsertDuplicateOverdueWarningWhenNotificationTaskRunsRepeatedly() {
        User user = createUser("odu-cas", "od-user-cas@example.com", "USER");
        User deviceAdmin = createUser("odda-cas", "od-device-admin-cas@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 11, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 11, 13, 0, 0));
        overdueNotificationProcessor.notifyAt(LocalDateTime.of(2026, 4, 11, 13, 30, 0));
        overdueNotificationProcessor.notifyAt(LocalDateTime.of(2026, 4, 11, 13, 31, 0));

        org.junit.jupiter.api.Assertions.assertEquals(1L, countNotifications(user.getId(), "OVERDUE_WARNING"));
        org.junit.jupiter.api.Assertions.assertEquals(1, loadNotificationSentFlag(borrowRecordId));
    }

    /**
     * 验证已经归还、不再处于逾期中的历史记录不会继续收到 C-06 逾期提醒，避免向已完成归还的用户补发过期告警。
     */
    @Test
    void shouldNotSendOverdueWarningForReturnedHistoricalOverdueRecord() {
        User user = createUser("odu-his1", "od-user-history-warning@example.com", "USER");
        User deviceAdmin = createUser("odda-his1", "od-device-admin-history-warning@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 13, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 13, 13, 0, 0));
        jdbcTemplate.update(
                "UPDATE borrow_record SET status = 'RETURNED', return_time = ?, updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 4, 13, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 13, 10, 0),
                borrowRecordId);

        overdueNotificationProcessor.notifyAt(LocalDateTime.of(2026, 4, 13, 13, 30, 0));

        org.junit.jupiter.api.Assertions.assertEquals(0L, countNotifications(user.getId(), "OVERDUE_WARNING"));
        org.junit.jupiter.api.Assertions.assertEquals(0, loadNotificationSentFlag(borrowRecordId));
    }

    /**
     * 验证逾期通知标记更新采用条件更新，第二次更新必须返回 0，避免并发任务重复占有同一条记录。
     */
    @Test
    void shouldUseCasWhenMarkingOverdueNotificationSent() {
        User user = createUser("odu-cas2", "od-user-cas2@example.com", "USER");
        User deviceAdmin = createUser("odda-cas2", "od-device-admin-cas2@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 12, 8, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 12, 13, 0, 0));
        String overdueRecordId = loadOverdueRecordByBorrowRecordId(borrowRecordId).get("id").toString();

        int first = overdueRecordMapper.updateNotificationSentIfPending(overdueRecordId, 1, LocalDateTime.of(2026, 4, 12, 13, 30, 0));
        int second = overdueRecordMapper.updateNotificationSentIfPending(overdueRecordId, 1, LocalDateTime.of(2026, 4, 12, 13, 31, 0));

        org.junit.jupiter.api.Assertions.assertEquals(1, first);
        org.junit.jupiter.api.Assertions.assertEquals(0, second);
    }

    /**
     * 验证 C-07 只释放已无 OVERDUE 借还记录的 RESTRICTED 用户，仍存在逾期借还的用户必须继续保持限制状态。
     */
    @Test
    void shouldReleaseOnlyEligibleRestrictedUsers() {
        User releasedUser = createUser("odu-rel1", "od-user-release-1@example.com", "USER");
        User retainedUser = createUser("odu-rel2", "od-user-release-2@example.com", "USER");
        User deviceAdmin = createUser("odda-rel", "od-device-admin-release@example.com", "DEVICE_ADMIN");
        Device firstDevice = createDevice();
        Device secondDevice = createDevice();
        String firstBorrowRecordId = createBorrowRecord(releasedUser, deviceAdmin, firstDevice, LocalDateTime.of(2026, 4, 5, 8, 0, 0));
        createBorrowRecord(retainedUser, deviceAdmin, secondDevice, LocalDateTime.of(2026, 4, 5, 8, 15, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 5, 13, 0, 0));
        jdbcTemplate.update(
                "UPDATE borrow_record SET status = 'RETURNED', return_time = ?, updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 4, 5, 13, 10, 0),
                LocalDateTime.of(2026, 4, 5, 13, 10, 0),
                firstBorrowRecordId);

        overdueRestrictionReleaseProcessor.releaseAt(LocalDateTime.of(2026, 4, 6, 2, 0, 0));

        org.junit.jupiter.api.Assertions.assertEquals("NORMAL", loadFreezeStatus(releasedUser.getId()));
        org.junit.jupiter.api.Assertions.assertEquals("RESTRICTED", loadFreezeStatus(retainedUser.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(2L, countNotifications(releasedUser.getId(), "ACCOUNT_FREEZE_UNFREEZE"));
    }

    /**
     * 验证手工设置为 RESTRICTED 的用户不会被 C-07 自动释放，防止系统误解除非逾期来源的人工限制。
     */
    @Test
    void shouldNotReleaseManuallyRestrictedUser() {
        User manualRestrictedUser = createUser("odu-manr", "od-user-manual-restricted@example.com", "USER");
        jdbcTemplate.update(
                "UPDATE `user` SET freeze_status = 'RESTRICTED', freeze_reason = '人工限制：违规操作', updated_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 4, 7, 1, 0, 0),
                manualRestrictedUser.getId());

        overdueRestrictionReleaseProcessor.releaseAt(LocalDateTime.of(2026, 4, 7, 2, 0, 0));

        org.junit.jupiter.api.Assertions.assertEquals("RESTRICTED", loadFreezeStatus(manualRestrictedUser.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(0L, countNotifications(manualRestrictedUser.getId(), "ACCOUNT_FREEZE_UNFREEZE"));
    }

    /**
     * 验证处理方式必须命中正式枚举 WARNING / COMPENSATION / CONTINUE，避免脏值写入逾期审计字段。
     */
    @Test
    void shouldRejectInvalidProcessingMethod() throws Exception {
        User user = createUser("odu-enum", "od-user-enum@example.com", "USER");
        User deviceAdmin = createUser("odda-enum", "od-device-admin-enum@example.com", "DEVICE_ADMIN");
        Device device = createDevice();
        String borrowRecordId = createBorrowRecord(user, deviceAdmin, device, LocalDateTime.of(2026, 4, 8, 8, 0, 0));
        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 8, 13, 0, 0));
        String overdueRecordId = loadOverdueRecordByBorrowRecordId(borrowRecordId).get("id").toString();

        mockMvc.perform(post("/api/overdue-records/{id}/process", overdueRecordId)
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processingMethod": "INVALID_METHOD",
                                  "remark": "非法枚举",
                                  "compensationAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String username, String email, String roleName) {
        Role role = roleMapper.findByName(roleName);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRoleId(role.getId());
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 统一创建可借设备，避免逾期测试再引入审批模式差异，保证关注点始终是 borrow_record -> overdue_record 的闭环。
     */
    private Device createDevice() {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("逾期测试分类-" + UuidUtil.randomUuid().substring(0, 8));
        category.setDefaultApprovalMode("DEVICE_ONLY");
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("逾期测试设备-" + UuidUtil.randomUuid().substring(0, 8));
        device.setDeviceNumber("OD-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("BORROWED");
        deviceMapper.insert(device);
        return device;
    }

    /**
     * 逾期治理以 borrow_record.expected_return_time 为唯一识别基准，因此这里直接造正式借还记录，
     * 避免测试被预约审批、签到等前置链路噪声干扰。
     */
    private String createBorrowRecord(User user, User deviceAdmin, Device device, LocalDateTime expectedReturnTime) {
        String reservationId = UuidUtil.randomUuid();
        LocalDateTime borrowTime = expectedReturnTime.minusHours(2);
        jdbcTemplate.update(
                "INSERT INTO reservation (id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, checked_in_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reservationId,
                user.getId(),
                user.getId(),
                "SELF",
                device.getId(),
                borrowTime.minusHours(1),
                expectedReturnTime,
                "逾期测试预约",
                "APPROVED",
                "DEVICE_ONLY",
                "CHECKED_IN",
                borrowTime.minusMinutes(30),
                borrowTime.minusHours(1),
                borrowTime.minusHours(1));

        String borrowRecordId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                "INSERT INTO borrow_record (id, reservation_id, device_id, user_id, borrow_time, expected_return_time, status, borrow_check_status, remark, operator_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                borrowRecordId,
                reservationId,
                device.getId(),
                user.getId(),
                borrowTime,
                expectedReturnTime,
                "BORROWED",
                "借出前检查正常",
                "逾期测试借出",
                deviceAdmin.getId(),
                borrowTime,
                borrowTime);
        return borrowRecordId;
    }

    private Map<String, Object> loadOverdueRecordByBorrowRecordId(String borrowRecordId) {
        return jdbcTemplate.query(
                        "SELECT id, overdue_hours, notification_sent FROM overdue_record WHERE borrow_record_id = ?",
                        this::singleMap,
                        borrowRecordId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String loadBorrowStatus(String borrowRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM borrow_record WHERE id = ?",
                String.class,
                borrowRecordId);
    }

    private String loadFreezeStatus(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT freeze_status FROM `user` WHERE id = ?",
                String.class,
                userId);
    }

    private int loadNotificationSentFlag(String borrowRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT notification_sent FROM overdue_record WHERE borrow_record_id = ?",
                Integer.class,
                borrowRecordId);
    }

    private long countNotifications(String userId, String notificationType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM notification_record WHERE user_id = ? AND notification_type = ?",
                Long.class,
                userId,
                notificationType);
        return count == null ? 0L : count;
    }

    private Map<String, Object> singleMap(ResultSet resultSet, int rowNum) throws SQLException {
        return Map.of(
                "id", resultSet.getString("id"),
                "overdue_hours", resultSet.getInt("overdue_hours"),
                "notification_sent", resultSet.getInt("notification_sent"));
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
