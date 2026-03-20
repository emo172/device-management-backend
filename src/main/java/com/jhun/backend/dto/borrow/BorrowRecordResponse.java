package com.jhun.backend.dto.borrow;

import java.time.LocalDateTime;

/**
 * 借还记录响应。
 *
 * @param id 借还记录 ID
 * @param reservationId 关联预约 ID
 * @param deviceId 关联设备 ID
 * @param deviceName 设备名称；当前接口在不额外引入前端拼装逻辑的前提下，优先回传真实设备展示名
 * @param deviceNumber 设备编号；用于管理端快速定位具体实物设备
 * @param userId 借用用户 ID
 * @param userName 借用用户展示名；优先使用实名，实名为空时回退用户名
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
        String deviceName,
        String deviceNumber,
        String userId,
        String userName,
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
