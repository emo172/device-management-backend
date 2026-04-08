package com.jhun.backend.integration.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.common.exception.MultiReservationConflictException;
import com.jhun.backend.dto.reservation.CreateMultiReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationDeviceMapper;
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
    private ReservationDeviceMapper reservationDeviceMapper;
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
     * 验证并发多设备预约下仍保持整单原子性：
     * 要么整单成功写入 1 条 reservation + 2 条 reservation_device，要么整单因为冲突完全回滚。
     */
    @Test
    void shouldKeepAtomicityUnderConcurrentMultiReservationRequests() throws Exception {
        long beforeReservationCount = reservationMapper.selectCount(null);
        long beforeReservationDeviceCount = reservationDeviceMapper.selectCount(null);
        Device firstDevice = createDevice();
        Device secondDevice = createDevice();
        User user = createUser();
        int concurrency = 30;
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(concurrency);
        List<Future<MultiReservationAttemptResult>> futures = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 23, 9, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 3, 23, 10, 0);

        for (int index = 0; index < concurrency; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    var response = reservationService.createMultiReservation(
                            user.getId(),
                            "USER",
                            new CreateMultiReservationRequest(
                                    null,
                                    List.of(firstDevice.getId(), secondDevice.getId()),
                                    startTime,
                                    endTime,
                                    "并发多设备预约",
                                    null));
                    return MultiReservationAttemptResult.successResult(response.reservation().id());
                } catch (MultiReservationConflictException exception) {
                    String reasonCode = exception.getResponse().blockingDevices().stream()
                            .findFirst()
                            .map(blockingDevice -> blockingDevice.reasonCode())
                            .orElse(null);
                    return MultiReservationAttemptResult.failure(reasonCode, null);
                } catch (BusinessException exception) {
                    return MultiReservationAttemptResult.failure(null, exception.getMessage());
                }
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        String successReservationId = null;
        for (Future<MultiReservationAttemptResult> future : futures) {
            MultiReservationAttemptResult result = future.get();
            if (result.success()) {
                successCount++;
                successReservationId = result.reservationId();
                continue;
            }
            assertNull(result.unexpectedFailureMessage());
            assertEquals("DEVICE_TIME_CONFLICT", result.failureReasonCode());
            conflictCount++;
        }
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, successCount);
        assertEquals(concurrency - 1, conflictCount);
        assertEquals(beforeReservationCount + 1, reservationMapper.selectCount(null));
        assertEquals(beforeReservationDeviceCount + 2, reservationDeviceMapper.selectCount(null));
        assertEquals(1, reservationMapper.countActiveReservationsByDeviceAndTimeRange(firstDevice.getId(), startTime, endTime));
        assertEquals(1, reservationMapper.countActiveReservationsByDeviceAndTimeRange(secondDevice.getId(), startTime, endTime));
        assertTrue(successReservationId != null && !successReservationId.isBlank());
        assertEquals(2L, reservationDeviceMapper.countByReservationId(successReservationId));
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

    /**
     * 多设备并发回归不能把非预期业务异常误判成“正常冲突”，
     * 因此这里同时记录冲突原因码与意外失败文案，确保测试只接受 1 次成功或明确的 DEVICE_TIME_CONFLICT。
     */
    private record MultiReservationAttemptResult(
            boolean success,
            String reservationId,
            String failureReasonCode,
            String unexpectedFailureMessage) {

        private static MultiReservationAttemptResult successResult(String reservationId) {
            return new MultiReservationAttemptResult(true, reservationId, null, null);
        }

        private static MultiReservationAttemptResult failure(String failureReasonCode, String unexpectedFailureMessage) {
            return new MultiReservationAttemptResult(false, null, failureReasonCode, unexpectedFailureMessage);
        }
    }

    private User createUser() {
        String suffix = UuidUtil.randomUuid().substring(0, 8);
        Role role = roleMapper.findByName("USER");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername("rsv-user-cc-" + suffix);
        user.setEmail("rsv-user-cc-" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName("并发测试用户");
        user.setPhone("13800" + suffix.substring(0, 6));
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private Device createDevice() {
        String suffix = UuidUtil.randomUuid().substring(0, 8);
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("并发预约分类-" + suffix);
        category.setSortOrder(1);
        category.setDescription("并发预约测试分类");
        category.setDefaultApprovalMode("DEVICE_ONLY");
        deviceCategoryMapper.insert(category);

        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("并发预约设备-" + suffix);
        device.setDeviceNumber("RSV-CONFLICT-" + suffix);
        device.setCategoryId(category.getId());
        device.setStatus("AVAILABLE");
        device.setDescription("并发冲突测试设备");
        device.setLocation("Lab-Concurrency");
        deviceMapper.insert(device);
        return device;
    }
}
