package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;

/**
 * 预约详情响应。
 *
 * @param id 预约 ID
 * @param batchId 预约批次 ID
 * @param userId 预约归属用户 ID
 * @param userName 预约归属用户名
 * @param createdBy 创建人 ID
 * @param createdByName 创建人用户名
 * @param reservationMode 预约模式
 * @param deviceId 设备 ID
 * @param deviceName 设备名称
 * @param deviceNumber 设备编号
 * @param deviceStatus 设备当前状态，用于详情页联动展示设备可用性
 * @param startTime 预约开始时间
 * @param endTime 预约结束时间
 * @param purpose 预约用途
 * @param remark 预约备注
 * @param status 预约状态
 * @param signStatus 签到状态
 * @param approvalModeSnapshot 审批模式快照
 * @param deviceApproverId 第一审审批人 ID
 * @param deviceApproverName 第一审审批人用户名
 * @param deviceApprovedAt 第一审时间
 * @param deviceApprovalRemark 第一审备注
 * @param systemApproverId 第二审审批人 ID
 * @param systemApproverName 第二审审批人用户名
 * @param systemApprovedAt 第二审时间
 * @param systemApprovalRemark 第二审备注
 * @param cancelReason 取消原因
 * @param cancelTime 取消时间
 * @param checkedInAt 签到时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ReservationDetailResponse(
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
        String deviceStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String remark,
        String status,
        String signStatus,
        String approvalModeSnapshot,
        String deviceApproverId,
        String deviceApproverName,
        LocalDateTime deviceApprovedAt,
        String deviceApprovalRemark,
        String systemApproverId,
        String systemApproverName,
        LocalDateTime systemApprovedAt,
        String systemApprovalRemark,
        String cancelReason,
        LocalDateTime cancelTime,
        LocalDateTime checkedInAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
