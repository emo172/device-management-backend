package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;

/**
 * 预约列表单项响应。
 *
 * @param id 预约 ID
 * @param batchId 预约批次 ID；单条预约为空，批量预约用于前端跳转批次视角
 * @param userId 预约归属用户 ID
 * @param userName 预约归属用户名，便于管理端列表直接展示
 * @param createdBy 创建人 ID；代预约场景与 userId 不同
 * @param createdByName 创建人用户名，便于区分本人预约与代预约
 * @param reservationMode 预约模式，固定为 SELF 或 ON_BEHALF
 * @param deviceId 设备 ID
 * @param deviceName 设备名称，供列表页直接展示
 * @param deviceNumber 设备编号，便于管理端快速定位实物设备
 * @param startTime 预约开始时间
 * @param endTime 预约结束时间
 * @param purpose 预约用途
 * @param status 预约状态，必须遵循 reservation_status 枚举口径
 * @param signStatus 签到状态，便于前端区分未签到与超时签到
 * @param approvalModeSnapshot 审批模式快照，防止设备配置变更后列表口径漂移
 * @param cancelReason 取消原因，只有取消后才有值
 * @param cancelTime 取消时间，供前端展示取消发生时点
 */
public record ReservationListItemResponse(
        String id,
        String batchId,
        String userId,
        String userName,
        String createdBy,
        String createdByName,
        String reservationMode,
        String deviceId,
        String deviceName,
        String deviceNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String status,
        String signStatus,
        String approvalModeSnapshot,
        String cancelReason,
        LocalDateTime cancelTime) {
}
