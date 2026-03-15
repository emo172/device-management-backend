package com.jhun.backend.integration.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 预约冲突并发测试。
 * <p>
 * 用于验证同设备同时间段高并发下只有一条预约可以成功创建，其余请求都被冲突检测拦截。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationConflictConcurrencyIT {

    @Autowired
    private ReservationService reservationService;
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

    /**
     * 验证 50 个并发请求抢占同一设备同一时段时，只允许 1 条预约成功。
     */
    @Test
    void shouldBlockConflictsUnderConcurrentRequests() throws Exception {
        Device device = createDevice();
        User user = createUser();
        int concurrency = 50;
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(concurrency);
        List<Future<ReservationAttemptResult>> futures = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 22, 9, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 3, 22, 10, 0);

        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    reservationService.createReservation(user.getId(), user.getId(), new CreateReservationRequest(
                            device.getId(),
                            startTime,
                            endTime,
                            "并发预约",
                            null));
                    return ReservationAttemptResult.successResult();
                }
                catch (BusinessException exception) {
                    return ReservationAttemptResult.failure(exception.getMessage());
                }
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<ReservationAttemptResult> future : futures) {
            ReservationAttemptResult result = future.get();
            if (result.success()) {
                successCount++;
                continue;
            }
            assertEquals("预约时间段冲突", result.failureMessage());
            conflictCount++;
        }
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, successCount);
        assertEquals(concurrency - 1, conflictCount);
        assertEquals(1, reservationMapper.countActiveReservationsByDeviceAndTimeRange(device.getId(), startTime, endTime));
    }

    /**
     * 并发测试不能吞掉所有异常；这里只接受成功或明确的预约冲突消息，防止数据库异常、线程异常被误判成预期失败。
     */
    private record ReservationAttemptResult(boolean success, String failureMessage) {

        private static ReservationAttemptResult successResult() {
            return new ReservationAttemptResult(true, null);
        }

        private static ReservationAttemptResult failure(String failureMessage) {
            assertTrue(failureMessage != null && !failureMessage.isBlank());
            return new ReservationAttemptResult(false, failureMessage);
        }
    }

    private User createUser() {
        Role role = roleMapper.findByName("USER");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername("rsv-user-cc");
        user.setEmail("rsv-user-cc@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName("并发测试用户");
        user.setPhone("13800138555");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice() {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("并发预约分类");
        category.setSortOrder(1);
        category.setDescription("并发预约测试分类");
        category.setDefaultApprovalMode("DEVICE_ONLY");
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("并发预约设备");
        device.setDeviceNumber("RSV-CONFLICT-001");
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("并发冲突测试设备");
        device.setLocation("Lab-Concurrency");
        deviceMapper.insert(device);
        return device;
    }
}
