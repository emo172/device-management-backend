package com.jhun.backend.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.ReservationDevice;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationDeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.service.support.reservation.ReservationDeviceBackfillSupport;
import com.jhun.backend.util.UuidUtil;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 预约聚合持久层集成测试。
 * <p>
 * 该测试专门锁定 T1 的三条核心迁移契约：
 * 1) 历史单设备预约会被回填到 {@code reservation_device}；
 * 2) 新写路径以关联表为真相源，不再把 {@code reservation.device_id} 当成长期写入真相；
 * 3) 新读路径即使遇到旧兼容列漂移，也仍以关联表为准恢复主设备信息。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationAggregatePersistenceIT {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationMapper reservationMapper;
    @Autowired
    private ReservationDeviceMapper reservationDeviceMapper;
    @Autowired
    private ReservationDeviceBackfillSupport reservationDeviceBackfillSupport;
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
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证历史单设备预约会被补成 1 条关联记录，且后续读路径优先信任关联表。
     */
    @Test
    void backfillsLegacyReservations() {
        User user = createUser("legacy-rsv-user", "legacy-rsv-user@example.com");
        Device device = createDevice("DEVICE_ONLY");
        String reservationId = insertLegacyReservation(user, device);

        assertThat(reservationDeviceMapper.findByReservationId(reservationId)).isEmpty();

        int insertedRows = reservationDeviceBackfillSupport.backfillLegacyReservations();

        List<ReservationDevice> relations = reservationDeviceMapper.findByReservationId(reservationId);
        assertThat(insertedRows).isEqualTo(1);
        assertThat(relations)
                .hasSize(1)
                .first()
                .satisfies(relation -> {
                    assertThat(relation.getReservationId()).isEqualTo(reservationId);
                    assertThat(relation.getDeviceId()).isEqualTo(device.getId());
                    assertThat(relation.getDeviceOrder()).isZero();
                });

        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.update("UPDATE reservation SET device_id = ? WHERE id = ?", "ghost-device-id", reservationId);

            Reservation aggregate = reservationMapper.findAggregateById(reservationId);
            assertThat(aggregate).isNotNull();
            assertThat(aggregate.getDeviceId()).isEqualTo(device.getId());
        } finally {
            jdbcTemplate.update("UPDATE reservation SET device_id = ? WHERE id = ?", device.getId(), reservationId);
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /**
     * 验证新建预约只把关联表当成设备真相源，不再向旧兼容列写入同一份主数据。
     */
    @Test
    void doesNotDualWriteLegacyDeviceId() {
        User user = createUser("aggregate-rsv-user", "aggregate-rsv-user@example.com");
        Device device = createDevice("DEVICE_ONLY");
        LocalDateTime startTime = alignedNow().plusDays(5).withHour(9).withMinute(0);
        LocalDateTime endTime = startTime.plusHours(1);

        String reservationId = reservationService.createReservation(
                        user.getId(),
                        user.getId(),
                        new CreateReservationRequest(device.getId(), startTime, endTime, "聚合持久层测试", "验证无双真相"))
                .id();

        String legacyDeviceId = jdbcTemplate.queryForObject(
                "SELECT device_id FROM reservation WHERE id = ?",
                String.class,
                reservationId);

        List<ReservationDevice> relations = reservationDeviceMapper.findByReservationId(reservationId);
        Reservation aggregate = reservationMapper.findAggregateById(reservationId);

        assertThat(legacyDeviceId).isNull();
        assertThat(relations)
                .hasSize(1)
                .first()
                .satisfies(relation -> {
                    assertThat(relation.getReservationId()).isEqualTo(reservationId);
                    assertThat(relation.getDeviceId()).isEqualTo(device.getId());
                    assertThat(relation.getDeviceOrder()).isZero();
                });
        assertThat(aggregate).isNotNull();
        assertThat(aggregate.getDeviceId()).isEqualTo(device.getId());
    }

    private String insertLegacyReservation(User user, Device device) {
        String reservationId = UuidUtil.randomUuid();
        LocalDateTime createdAt = alignedNow();
        LocalDateTime startTime = createdAt.plusDays(3).withHour(10).withMinute(0);
        LocalDateTime endTime = startTime.plusHours(1);
        jdbcTemplate.update(
                """
                INSERT INTO reservation (
                    id, batch_id, user_id, created_by, reservation_mode, device_id,
                    start_time, end_time, purpose, status, approval_mode_snapshot,
                    remark, sign_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reservationId,
                null,
                user.getId(),
                user.getId(),
                "SELF",
                device.getId(),
                Timestamp.valueOf(startTime),
                Timestamp.valueOf(endTime),
                "历史单设备预约",
                "PENDING_DEVICE_APPROVAL",
                "DEVICE_ONLY",
                "等待回填",
                "NOT_CHECKED_IN",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt));
        return reservationId;
    }

    private User createUser(String username, String email) {
        Role role = roleMapper.findByName("USER");
        String suffix = UuidUtil.randomUuid().replace("-", "").substring(0, 6);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername((username.substring(0, Math.min(username.length(), 13)) + suffix).substring(0, 19));
        user.setEmail(email.replace("@", "+" + suffix + "@"));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138666");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice(String approvalMode) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("聚合预约分类-" + UuidUtil.randomUuid().substring(0, 6));
        category.setSortOrder(1);
        category.setDescription("预约聚合持久层测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("聚合预约设备-" + UuidUtil.randomUuid().substring(0, 6));
        device.setDeviceNumber("RSV-AGG-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约聚合持久层测试设备");
        device.setLocation("Lab-Aggregate");
        deviceMapper.insert(device);
        return device;
    }

    private LocalDateTime alignedNow() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
