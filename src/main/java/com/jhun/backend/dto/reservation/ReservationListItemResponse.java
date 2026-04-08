package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;
import java.util.List;

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
 * @param deviceName 设备名称，兼容旧列表展示，固定映射到主设备
 * @param deviceNumber 设备编号，兼容旧列表展示，固定映射到主设备
 * @param deviceCount 当前预约关联的设备数量
 * @param devices 当前预约关联的完整设备摘要列表，顺序与 reservation_device.device_order 一致
 * @param primaryDeviceId 主设备 ID；旧字段的正式映射来源
 * @param primaryDeviceName 主设备名称；旧字段的正式映射来源
 * @param primaryDeviceNumber 主设备编号；旧字段的正式映射来源
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
        int deviceCount,
        List<ReservationDeviceSummaryResponse> devices,
        String primaryDeviceId,
        String primaryDeviceName,
        String primaryDeviceNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String status,
        String signStatus,
        String approvalModeSnapshot,
        String cancelReason,
        LocalDateTime cancelTime) {
}
