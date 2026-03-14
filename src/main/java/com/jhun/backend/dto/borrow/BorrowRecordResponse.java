package com.jhun.backend.dto.borrow;

import java.time.LocalDateTime;

/**
 * 借还记录响应。
 *
 * @param id 借还记录 ID
 * @param reservationId 关联预约 ID
 * @param deviceId 关联设备 ID
 * @param userId 借用用户 ID
 * @param borrowTime 实际借出时间
 * @param returnTime 实际归还时间
 * @param expectedReturnTime 预计归还时间
 * @param status 借还状态
 * @param borrowCheckStatus 借出前检查记录
 * @param returnCheckStatus 归还时检查记录
 * @param remark 备注说明
 * @param operatorId 借出确认操作人 ID
 * @param returnOperatorId 归还确认操作人 ID
 */
public record BorrowRecordResponse(
        String id,
        String reservationId,
        String deviceId,
        String userId,
        LocalDateTime borrowTime,
        LocalDateTime returnTime,
        LocalDateTime expectedReturnTime,
        String status,
        String borrowCheckStatus,
        String returnCheckStatus,
        String remark,
        String operatorId,
        String returnOperatorId) {
}
