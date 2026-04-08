package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约工作流响应。
 * <p>
 * 该 DTO 面向创建、一审、二审、签到和人工处理等动作型接口，
 * 直接回传前端继续渲染当前页面所需的预约、设备、审批人与关键时间字段，
 * 避免动作完成后还要依赖前端自行拼装上下文。
 * 其中关键字段分组如下：
 * <ul>
 *     <li>预约主体：{@code id}、{@code userId}、{@code userName}、{@code createdBy}、{@code createdByName}</li>
 *     <li>预约快照：{@code reservationMode}、{@code approvalModeSnapshot}、{@code status}、{@code signStatus}</li>
 *     <li>设备与时间：旧兼容字段 {@code deviceId/deviceName/deviceNumber}、新字段 {@code deviceCount/devices[]/primaryDevice*}、以及 {@code startTime/endTime}</li>
 *     <li>审批轨迹：设备侧与系统侧审批人、审批时间、审批备注</li>
 *     <li>收尾字段：{@code cancelReason}、{@code cancelTime}、{@code checkedInAt}、{@code createdAt}、{@code updatedAt}</li>
 * </ul>
 * 这样前端在创建成功页、待审批页、详情页和签到页之间切换时，可以始终消费同一份动作回包契约。
 */
public record ReservationResponse(
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
