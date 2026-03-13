package com.jhun.backend.service.support.reservation;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.entity.Device;
import org.springframework.stereotype.Component;

/**
 * 预约校验器。
 * <p>
 * 负责校验预约时间范围，并根据设备覆盖审批模式与分类默认审批模式确定预约状态机入口。
 */
@Component
public class ReservationValidator {

    public void validateCreateRequest(CreateReservationRequest request) {
        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BusinessException("预约开始时间必须早于结束时间");
        }
    }

    public String resolveApprovalMode(Device device, String categoryDefaultApprovalMode) {
        return device.getApprovalModeOverride() != null && !device.getApprovalModeOverride().isBlank()
                ? device.getApprovalModeOverride()
                : categoryDefaultApprovalMode;
    }

    public String resolveInitialStatus(Device device, String categoryDefaultApprovalMode) {
        String approvalMode = resolveApprovalMode(device, categoryDefaultApprovalMode);
        return "DEVICE_THEN_SYSTEM".equals(approvalMode)
                ? "PENDING_DEVICE_APPROVAL"
                : "PENDING_DEVICE_APPROVAL";
    }
}
