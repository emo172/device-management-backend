package com.jhun.backend.integration.statistics;

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
import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 统计控制器集成测试。
 * <p>
 * 该测试直接向 {@code statistics_daily} 写入聚合结果，再通过统计接口读取，
 * 用于保护统计查询接口的返回结构、对象名称映射和 SYSTEM_ADMIN 权限边界。
 */
@SpringBootTest
@ActiveProfiles("test")
class StatisticsControllerIntegrationTest {

    private static final LocalDate STAT_DATE = LocalDate.of(2026, 3, 28);

    @Autowired
    private WebApplicationContext webApplicationContext;

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
    }

    /**
     * 验证系统管理员可以读取总览、利用率、借用统计、逾期统计和排行榜接口，
     * 防止统计页所需的多个聚合视图在控制层丢字段或取错名称映射。
     */
    @Test
    void shouldReturnStatisticsViewsForSystemAdmin() throws Exception {
        User systemAdmin = createUser("statistics-admin", "statistics-admin@example.com", "SYSTEM_ADMIN");
        seedReferenceData();
        seedAggregatedStatistics();

        mockMvc.perform(get("/api/statistics/overview")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statDate").value(STAT_DATE.toString()))
                .andExpect(jsonPath("$.data.totalReservations").value(6))
                .andExpect(jsonPath("$.data.approvedReservations").value(4))
                .andExpect(jsonPath("$.data.totalBorrows").value(3))
                .andExpect(jsonPath("$.data.totalOverdue").value(1))
                .andExpect(jsonPath("$.data.totalOverdueHours").value(6));

        mockMvc.perform(get("/api/statistics/device-utilization")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceId").value("stat-device-1"))
                .andExpect(jsonPath("$.data[0].deviceName").value("示波器-A"))
                .andExpect(jsonPath("$.data[0].utilizationRate").value(75.00));

        mockMvc.perform(get("/api/statistics/category-utilization")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryId").value("stat-category-1"))
                .andExpect(jsonPath("$.data[0].categoryName").value("实验设备"));

        mockMvc.perform(get("/api/statistics/borrow")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBorrows").value(3))
                .andExpect(jsonPath("$.data.totalReturns").value(2));

        mockMvc.perform(get("/api/statistics/overdue")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOverdue").value(1))
                .andExpect(jsonPath("$.data.totalOverdueHours").value(6));

        mockMvc.perform(get("/api/statistics/hot-time-slots")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].timeSlot").value("09"))
                .andExpect(jsonPath("$.data[0].totalReservations").value(3));

        mockMvc.perform(get("/api/statistics/device-ranking")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceId").value("stat-device-1"))
                .andExpect(jsonPath("$.data[0].totalBorrows").value(3));

        mockMvc.perform(get("/api/statistics/user-ranking")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value("stat-user-1"))
                .andExpect(jsonPath("$.data[0].username").value("stat-rank-user-1"))
                .andExpect(jsonPath("$.data[0].totalBorrows").value(2));
    }

    /**
     * 验证普通用户不能访问统计接口，保护统计分析仅由 SYSTEM_ADMIN 使用的权限边界。
     */
    @Test
    void shouldRejectUserAccessingStatisticsOverview() throws Exception {
        User user = createUser("statistics-user", "statistics-user@example.com", "USER");

        mockMvc.perform(get("/api/statistics/overview")
                        .param("date", STAT_DATE.toString())
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isForbidden());
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
        user.setPhone("13800138123");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 统计接口需要把 subject_value 翻译为可读名称，因此预先补齐设备、分类和用户参照数据。
     */
    private void seedReferenceData() {
        jdbcTemplate.update(
                "INSERT INTO device_category (id, name, parent_id, sort_order, description, default_approval_mode) VALUES (?, ?, ?, ?, ?, ?)",
                "stat-category-1", "实验设备", null, 1, "统计测试分类", "DEVICE_ONLY");
        jdbcTemplate.update(
                "INSERT INTO device (id, name, device_number, category_id, status, approval_mode_override, location) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "stat-device-1", "示波器-A", "STAT-DEVICE-001", "stat-category-1", "AVAILABLE", "DEVICE_ONLY", "实验室 A");
        jdbcTemplate.update(
                "INSERT INTO device (id, name, device_number, category_id, status, approval_mode_override, location) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "stat-device-2", "热像仪-B", "STAT-DEVICE-002", "stat-category-1", "AVAILABLE", "DEVICE_ONLY", "实验室 B");

        jdbcTemplate.update(
                "INSERT INTO `user` (id, username, email, password_hash, role_id, real_name, phone, status, freeze_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "stat-user-1", "stat-rank-user-1", "stat-user-1@example.com", passwordEncoder.encode("Password123!"),
                roleMapper.findByName("USER").getId(), "统计用户一", "13800138021", 1, "NORMAL");
        jdbcTemplate.update(
                "INSERT INTO `user` (id, username, email, password_hash, role_id, real_name, phone, status, freeze_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "stat-user-2", "stat-rank-user-2", "stat-user-2@example.com", passwordEncoder.encode("Password123!"),
                roleMapper.findByName("USER").getId(), "统计用户二", "13800138022", 1, "NORMAL");
    }

    /**
     * 这里直接写入聚合结果，专注验证统计接口读取逻辑，而不把失败原因混入聚合任务本身。
     */
    private void seedAggregatedStatistics() {
        insertStatistics("stat-overview-device", "DEVICE_UTILIZATION", "DAY", "GLOBAL", "ALL", 6, 4, 1, 1, 0, 3, 2, 0, 0, 62.50);
        insertStatistics("stat-overview-category", "CATEGORY_UTILIZATION", "DAY", "GLOBAL", "ALL", 6, 4, 1, 1, 0, 3, 2, 0, 0, 62.50);
        insertStatistics("stat-overview-borrow", "USER_BORROW", "DAY", "GLOBAL", "ALL", 0, 0, 0, 0, 0, 3, 2, 0, 0, null);
        insertStatistics("stat-overview-overdue", "OVERDUE_STAT", "DAY", "GLOBAL", "ALL", 0, 0, 0, 0, 0, 0, 0, 1, 6, null);

        insertStatistics("stat-device-1-row", "DEVICE_UTILIZATION", "DAY", "DEVICE", "stat-device-1", 4, 3, 0, 1, 0, 3, 2, 1, 6, 75.00);
        insertStatistics("stat-device-2-row", "DEVICE_UTILIZATION", "DAY", "DEVICE", "stat-device-2", 2, 1, 1, 0, 0, 0, 0, 0, 0, 25.00);

        insertStatistics("stat-category-1-row", "CATEGORY_UTILIZATION", "DAY", "CATEGORY", "stat-category-1", 6, 4, 1, 1, 0, 3, 2, 1, 6, 62.50);

        insertStatistics("stat-user-1-row", "USER_BORROW", "DAY", "USER", "stat-user-1", 0, 0, 0, 0, 0, 2, 1, 1, 6, null);
        insertStatistics("stat-user-2-row", "USER_BORROW", "DAY", "USER", "stat-user-2", 0, 0, 0, 0, 0, 1, 1, 0, 0, null);

        insertStatistics("stat-slot-09", "TIME_DISTRIBUTION", "DAY", "TIME_SLOT", "09", 3, 2, 0, 1, 0, 0, 0, 0, 0, null);
        insertStatistics("stat-slot-14", "TIME_DISTRIBUTION", "DAY", "TIME_SLOT", "14", 2, 2, 0, 0, 0, 0, 0, 0, 0, null);
        insertStatistics("stat-slot-20", "TIME_DISTRIBUTION", "DAY", "TIME_SLOT", "20", 1, 0, 1, 0, 0, 0, 0, 0, 0, null);
    }

    private void insertStatistics(
            String id,
            String statType,
            String granularity,
            String subjectType,
            String subjectValue,
            int totalReservations,
            int approvedReservations,
            int rejectedReservations,
            int cancelledReservations,
            int expiredReservations,
            int totalBorrows,
            int totalReturns,
            int totalOverdue,
            int totalOverdueHours,
            Double utilizationRate) {
        jdbcTemplate.update(
                "INSERT INTO statistics_daily (id, stat_date, stat_type, granularity, subject_type, subject_value, total_reservations, approved_reservations, rejected_reservations, cancelled_reservations, expired_reservations, total_borrows, total_returns, total_overdue, total_overdue_hours, utilization_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                Date.valueOf(STAT_DATE),
                statType,
                granularity,
                subjectType,
                subjectValue,
                totalReservations,
                approvedReservations,
                rejectedReservations,
                cancelledReservations,
                expiredReservations,
                totalBorrows,
                totalReturns,
                totalOverdue,
                totalOverdueHours,
                utilizationRate);
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
