package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.scheduler.reservation.BorrowConfirmTimeoutProcessor;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 预约服务单元测试。
 * <p>
 * 本测试用于锁定签到窗口和待人工处理核心语义，防止 Task 10 规则在后续迭代中回退。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationMapper reservationMapper;
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
    private BorrowConfirmTimeoutProcessor borrowConfirmTimeoutProcessor;

    /**
     * 验证开始后 30~60 分钟签到会进入超时签到状态。
     */
    @Test
    void shouldMarkCheckInTimeoutStatusWhenLateCheckIn() {
        User user = createUser("svc-reserve-user-1", "svc-reserve-user-1@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY", "SVC-RSV-01");
        Reservation reservation = createApprovedReservation(user, user, device,
                LocalDateTime.of(2026, 3, 22, 9, 0),
                LocalDateTime.of(2026, 3, 22, 10, 0));

        ReservationResponse response = reservationService.checkIn(
                reservation.getId(),
                user.getId(),
                "USER",
                new CheckInRequest(LocalDateTime.of(2026, 3, 22, 9, 50)));

        assertEquals("CHECKED_IN_TIMEOUT", response.signStatus());
    }

    /**
     * 验证已签到预约超过 2 小时未确认借用会转为待人工处理。
     */
    @Test
    void shouldMoveReservationToPendingManualWhenBorrowConfirmTimeout() {
        User user = createUser("svc-reserve-user-2", "svc-reserve-user-2@example.com", "USER");
        Device device = createDevice("DEVICE_ONLY", "SVC-RSV-02");
        Reservation reservation = createApprovedReservation(user, user, device,
                LocalDateTime.now().minusHours(5),
                LocalDateTime.now().minusHours(4));
        reservation.setSignStatus("CHECKED_IN");
        reservation.setCheckedInAt(LocalDateTime.now().minusHours(3));
        reservationMapper.updateById(reservation);

        borrowConfirmTimeoutProcessor.processBorrowConfirmTimeoutReservations();

        Reservation refreshed = reservationMapper.selectById(reservation.getId());
        assertEquals("PENDING_MANUAL", refreshed.getStatus());
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
        user.setPhone("13800138666");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice(String approvalMode, String deviceNumber) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("预约服务测试分类-" + deviceNumber);
        category.setSortOrder(1);
        category.setDescription("预约服务测试分类");
        category.setDefaultApprovalMode(approvalMode);
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("预约服务测试设备-" + deviceNumber);
        device.setDeviceNumber(deviceNumber);
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("预约服务测试设备");
        device.setLocation("Lab-Reservation-Service");
        deviceMapper.insert(device);
        return device;
    }

    private Reservation createApprovedReservation(
            User user,
            User creator,
            Device device,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        Reservation reservation = new Reservation();
        reservation.setId(UuidUtil.randomUuid());
        reservation.setUserId(user.getId());
        reservation.setCreatedBy(creator.getId());
        reservation.setReservationMode("SELF");
        reservation.setDeviceId(device.getId());
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setPurpose("预约服务测试");
        reservation.setRemark("Task10 单测");
        reservation.setApprovalModeSnapshot("DEVICE_ONLY");
        reservation.setStatus("APPROVED");
        reservation.setSignStatus("NOT_CHECKED_IN");
        reservationMapper.insert(reservation);
        return reservation;
    }
}
