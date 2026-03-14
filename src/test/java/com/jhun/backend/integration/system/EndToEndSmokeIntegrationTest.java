package com.jhun.backend.integration.system;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 端到端 smoke 集成测试。
 * <p>
 * 该测试覆盖“业务事实数据 -> 手动触发统计聚合 -> 统计接口查询”的最小闭环，
 * 用于防止统计任务只写了接口读取却没有真正从预约、借还和逾期事实数据汇总。
 */
@SpringBootTest
@ActiveProfiles("test")
class EndToEndSmokeIntegrationTest {

    private static final LocalDate STAT_DATE = LocalDate.of(2026, 3, 28);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanBusinessFacts();
    }

    /**
     * 验证统计聚合任务能够从真实业务表汇总，并让统计接口读到对应结果，
     * 以保护 C-08 统计预聚合任务的最小业务闭环。
     */
    @Test
    void shouldAggregateBusinessFactsAndExposeStatisticsEndpoints() throws Exception {
        User systemAdmin = createUser("smoke-system-admin", "smoke-system-admin@example.com", "SYSTEM_ADMIN");
        User deviceAdmin = createUser("smoke-device-admin", "smoke-device-admin@example.com", "DEVICE_ADMIN");
        User borrowUser = createUser("smoke-user", "smoke-user@example.com", "USER");

        seedBusinessFacts(deviceAdmin.getId(), borrowUser.getId());
        triggerStatisticsAggregation(STAT_DATE);

        mockMvc.perform(get("/api/statistics/overview")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReservations").value(1))
                .andExpect(jsonPath("$.data.approvedReservations").value(1))
                .andExpect(jsonPath("$.data.totalBorrows").value(1))
                .andExpect(jsonPath("$.data.totalReturns").value(0))
                .andExpect(jsonPath("$.data.totalOverdue").value(1))
                .andExpect(jsonPath("$.data.totalOverdueHours").value(5));

        mockMvc.perform(get("/api/statistics/device-ranking")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceId").value("smoke-device-1"))
                .andExpect(jsonPath("$.data[0].deviceName").value("烟气分析仪"))
                .andExpect(jsonPath("$.data[0].totalBorrows").value(1));

        mockMvc.perform(get("/api/statistics/user-ranking")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(borrowUser.getId()))
                .andExpect(jsonPath("$.data[0].username").value("smoke-user"))
                .andExpect(jsonPath("$.data[0].totalBorrows").value(1));
    }

    /**
     * 验证“前一天借出、当天归还”的记录会被当天统计为归还，
     * 防止聚合逻辑错误地继续按借出时间统计归还数，导致跨天归还漏算。
     */
    @Test
    void shouldCountReturnOnReturnDateWhenBorrowHappenedPreviousDay() throws Exception {
        User systemAdmin = createUser("smoke-system-admin-2", "smoke-system-admin-2@example.com", "SYSTEM_ADMIN");
        User deviceAdmin = createUser("smoke-device-admin-2", "smoke-device-admin-2@example.com", "DEVICE_ADMIN");
        User borrowUser = createUser("smoke-user-2", "smoke-user-2@example.com", "USER");

        seedCrossDayReturnFacts(deviceAdmin.getId(), borrowUser.getId());
        triggerStatisticsAggregation(STAT_DATE);

        mockMvc.perform(get("/api/statistics/borrow")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBorrows").value(0))
                .andExpect(jsonPath("$.data.totalReturns").value(1));
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
        user.setPhone("13800138124");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 构造一条已经审批通过并已借出的预约事实，同时补齐逾期记录，
     * 让聚合任务可以同时覆盖预约、借还、逾期、设备、分类和用户多个维度。
     */
    private void seedBusinessFacts(String deviceAdminId, String borrowUserId) {
        jdbcTemplate.update(
                "INSERT INTO device_category (id, name, parent_id, sort_order, description, default_approval_mode) VALUES (?, ?, ?, ?, ?, ?)",
                "smoke-category-1", "环境监测设备", null, 1, "smoke 分类", "DEVICE_ONLY");
        jdbcTemplate.update(
                "INSERT INTO device (id, name, device_number, category_id, status, approval_mode_override, location) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "smoke-device-1", "烟气分析仪", "SMOKE-DEVICE-001", "smoke-category-1", "BORROWED", "DEVICE_ONLY", "实验楼 101");

        jdbcTemplate.update(
                "INSERT INTO reservation (id, batch_id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, checked_in_at, device_approver_id, device_approved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "smoke-reservation-1", null, borrowUserId, borrowUserId, "SELF", "smoke-device-1",
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 9, 0)),
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 11, 0)),
                "环境检测实验", "APPROVED", "DEVICE_ONLY", "CHECKED_IN",
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 8, 50)),
                deviceAdminId,
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 27, 18, 0)));

        jdbcTemplate.update(
                "INSERT INTO borrow_record (id, reservation_id, device_id, user_id, borrow_time, expected_return_time, status, borrow_check_status, remark, operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "smoke-borrow-1", "smoke-reservation-1", "smoke-device-1", borrowUserId,
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 9, 5)),
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 11, 0)),
                "BORROWED", "设备状态正常", "smoke 借出", deviceAdminId);

        jdbcTemplate.update(
                "INSERT INTO overdue_record (id, borrow_record_id, user_id, device_id, overdue_hours, overdue_days, processing_status, compensation_amount, notification_sent, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "smoke-overdue-1", "smoke-borrow-1", borrowUserId, "smoke-device-1", 5, 1, "PENDING", 0.00, 0,
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 16, 0)));
    }

    /**
     * 构造跨天归还场景：借出发生在统计日前一天，归还发生在统计日当天。
     */
    private void seedCrossDayReturnFacts(String deviceAdminId, String borrowUserId) {
        jdbcTemplate.update(
                "INSERT INTO device_category (id, name, parent_id, sort_order, description, default_approval_mode) VALUES (?, ?, ?, ?, ?, ?)",
                "smoke-category-2", "跨天归还设备", null, 2, "smoke 跨天分类", "DEVICE_ONLY");
        jdbcTemplate.update(
                "INSERT INTO device (id, name, device_number, category_id, status, approval_mode_override, location) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "smoke-device-2", "频谱分析仪", "SMOKE-DEVICE-002", "smoke-category-2", "AVAILABLE", "DEVICE_ONLY", "实验楼 102");
        jdbcTemplate.update(
                "INSERT INTO reservation (id, batch_id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, checked_in_at, device_approver_id, device_approved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "smoke-reservation-2", null, borrowUserId, borrowUserId, "SELF", "smoke-device-2",
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 27, 13, 0)),
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 10, 0)),
                "跨天归还实验", "APPROVED", "DEVICE_ONLY", "CHECKED_IN",
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 27, 12, 55)),
                deviceAdminId,
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 27, 10, 0)));
        jdbcTemplate.update(
                "INSERT INTO borrow_record (id, reservation_id, device_id, user_id, borrow_time, return_time, expected_return_time, status, borrow_check_status, return_check_status, remark, operator_id, return_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "smoke-borrow-2", "smoke-reservation-2", "smoke-device-2", borrowUserId,
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 27, 13, 10)),
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 9, 30)),
                Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 10, 0)),
                "RETURNED", "借出正常", "归还正常", "跨天归还", deviceAdminId, deviceAdminId);
    }

    /**
     * smoke 测试共用同一个 Spring 上下文，因此在每次执行前清理本类写入的业务事实，避免场景互相污染。
     */
    private void cleanBusinessFacts() {
        jdbcTemplate.update("DELETE FROM statistics_daily");
        jdbcTemplate.update("DELETE FROM overdue_record WHERE id IN ('smoke-overdue-1')");
        jdbcTemplate.update("DELETE FROM borrow_record WHERE id IN ('smoke-borrow-1', 'smoke-borrow-2')");
        jdbcTemplate.update("DELETE FROM reservation WHERE id IN ('smoke-reservation-1', 'smoke-reservation-2')");
        jdbcTemplate.update("DELETE FROM device WHERE id IN ('smoke-device-1', 'smoke-device-2')");
        jdbcTemplate.update("DELETE FROM device_category WHERE id IN ('smoke-category-1', 'smoke-category-2')");
        jdbcTemplate.update("DELETE FROM `user` WHERE username IN ('smoke-system-admin', 'smoke-device-admin', 'smoke-user', 'smoke-system-admin-2', 'smoke-device-admin-2', 'smoke-user-2')");
    }

    /**
     * smoke 测试禁止依赖真实调度器，因此直接获取任务 Bean 并反射调用按日聚合方法。
     */
    private void triggerStatisticsAggregation(LocalDate statDate) throws Exception {
        Object processor = applicationContext.getBean("statisticsAggregationProcessor");
        Method method = processor.getClass().getMethod("aggregateForDate", LocalDate.class);
        method.invoke(processor, statDate);
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
