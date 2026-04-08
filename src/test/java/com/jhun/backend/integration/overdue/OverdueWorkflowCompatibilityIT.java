package com.jhun.backend.integration.overdue;

import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.scheduler.overdue.OverdueAutoDetectProcessor;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 多设备预约逾期兼容性测试。
 * <p>
 * T5 调整后，逾期链路需要接受“同一 reservation 聚合下存在多条 borrow_record”的事实，
 * 同时仍然保证用户冻结状态不会因为逐设备扇出而发生额外分叉。
 */
@SpringBootTest
@ActiveProfiles("test")
class OverdueWorkflowCompatibilityIT {

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
    @Autowired
    private OverdueAutoDetectProcessor overdueAutoDetectProcessor;

    /**
     * 验证同一多设备预约聚合下的 borrow_record 会一起转成 OVERDUE，并生成逐设备 overdue_record。
     * <p>
     * 断言里同时检查用户冻结通知只有 1 条，避免内部扇出把外部用户状态也错误放大成多份副作用。
     */
    @Test
    void createsOverdueRecordsWithoutStateDivergence() {
        User user = createUser("odw-u1", "overdue-workflow-user-1@example.com", "USER");
        User deviceAdmin = createUser("odw-da1", "overdue-workflow-device-admin-1@example.com", "DEVICE_ADMIN");
        Device firstDevice = createBorrowedDevice();
        Device secondDevice = createBorrowedDevice();
        String reservationId = insertBorrowedMultiDeviceReservation(
                user,
                deviceAdmin,
                List.of(firstDevice, secondDevice),
                LocalDateTime.of(2026, 4, 24, 10, 20, 0),
                LocalDateTime.of(2026, 4, 24, 12, 0, 0));

        overdueAutoDetectProcessor.detectAt(LocalDateTime.of(2026, 4, 24, 16, 30, 0));

        org.junit.jupiter.api.Assertions.assertEquals(2L, countOverdueRecordsByReservationId(reservationId));
        org.junit.jupiter.api.Assertions.assertEquals(2L, countBorrowRowsByReservationIdAndStatus(reservationId, "OVERDUE"));
        org.junit.jupiter.api.Assertions.assertEquals("RESTRICTED", loadFreezeStatus(user.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(1L, countNotifications(user.getId(), "ACCOUNT_FREEZE_UNFREEZE"));
    }

    private String insertBorrowedMultiDeviceReservation(
            User user,
            User deviceAdmin,
            List<Device> devices,
            LocalDateTime borrowTime,
            LocalDateTime expectedReturnTime) {
        String reservationId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                "INSERT INTO reservation (id, user_id, created_by, reservation_mode, device_id, start_time, end_time, purpose, status, approval_mode_snapshot, sign_status, checked_in_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reservationId,
                user.getId(),
                user.getId(),
                "SELF",
                devices.getFirst().getId(),
                borrowTime.minusMinutes(20),
                expectedReturnTime,
                "OverdueWorkflowCompatibilityIT",
                "APPROVED",
                "DEVICE_ONLY",
                "CHECKED_IN",
                borrowTime.minusMinutes(10),
                borrowTime.minusHours(1),
                borrowTime.minusHours(1));

        for (int index = 0; index < devices.size(); index++) {
            Device device = devices.get(index);
            jdbcTemplate.update(
                    "INSERT INTO reservation_device (id, reservation_id, device_id, device_order, created_at) VALUES (?, ?, ?, ?, ?)",
                    UuidUtil.randomUuid(),
                    reservationId,
                    device.getId(),
                    index,
                    borrowTime.minusHours(1));
            jdbcTemplate.update(
                    "INSERT INTO borrow_record (id, reservation_id, device_id, user_id, borrow_time, expected_return_time, status, borrow_check_status, remark, operator_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UuidUtil.randomUuid(),
                    reservationId,
                    device.getId(),
                    user.getId(),
                    borrowTime,
                    expectedReturnTime,
                    "BORROWED",
                    "两台设备已一起借出",
                    "多设备预约逾期测试",
                    deviceAdmin.getId(),
                    borrowTime,
                    borrowTime);
        }
        return reservationId;
    }

    private long countOverdueRecordsByReservationId(String reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM overdue_record o INNER JOIN borrow_record b ON b.id = o.borrow_record_id WHERE b.reservation_id = ?",
                Long.class,
                reservationId);
        return count == null ? 0L : count;
    }

    private long countBorrowRowsByReservationIdAndStatus(String reservationId, String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM borrow_record WHERE reservation_id = ? AND status = ?",
                Long.class,
                reservationId,
                status);
        return count == null ? 0L : count;
    }

    private String loadFreezeStatus(String userId) {
        return jdbcTemplate.queryForObject(
                "SELECT freeze_status FROM `user` WHERE id = ?",
                String.class,
                userId);
    }

    private long countNotifications(String userId, String notificationType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM notification_record WHERE user_id = ? AND notification_type = ?",
                Long.class,
                userId,
                notificationType);
        return count == null ? 0L : count;
    }

    private Device createBorrowedDevice() {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("逾期兼容测试分类-" + UuidUtil.randomUuid().substring(0, 8));
        category.setDefaultApprovalMode("DEVICE_ONLY");
        category.setSortOrder(1);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("逾期兼容测试设备-" + UuidUtil.randomUuid().substring(0, 8));
        device.setDeviceNumber("OWF-" + UuidUtil.randomUuid().substring(0, 8));
        device.setCategoryId(category.getId());
        device.setStatus("BORROWED");
        device.setDescription("OverdueWorkflowCompatibilityIT 测试设备");
        device.setLocation("Overdue-Compatibility-Lab");
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
}
