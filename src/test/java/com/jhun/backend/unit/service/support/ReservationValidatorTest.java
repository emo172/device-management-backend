package com.jhun.backend.unit.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.service.support.reservation.ReservationValidator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 预约校验器测试。
 * <p>
 * 用于验证审批模式映射与时间窗口校验，确保预约创建前的基础业务规则不会被绕过。
 */
class ReservationValidatorTest {

    private final ReservationValidator reservationValidator = new ReservationValidator();

    /**
     * 验证设备覆盖审批模式为 DEVICE_ONLY 时，预约初始状态应进入待设备审批。
     */
    @Test
    void shouldCreateReservationWithDeviceApprovalStatus() {
        Device device = new Device();
        device.setApprovalModeOverride("DEVICE_ONLY");

        String status = reservationValidator.resolveInitialStatus(device, "DEVICE_THEN_SYSTEM");

        assertEquals("PENDING_DEVICE_APPROVAL", status);
    }

    /**
     * 验证预约时间区间必须满足开始时间早于结束时间，保护 SQL 的时间范围约束。
     */
    @Test
    void shouldRejectInvalidReservationTimeRange() {
        CreateReservationRequest request = new CreateReservationRequest(
                "device-1",
                LocalDateTime.of(2026, 3, 15, 10, 0),
                LocalDateTime.of(2026, 3, 15, 9, 30),
                "课程使用",
                null);

        assertThrows(BusinessException.class, () -> reservationValidator.validateCreateRequest(request));
    }
}
