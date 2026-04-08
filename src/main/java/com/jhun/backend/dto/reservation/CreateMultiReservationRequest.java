package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多设备单预约创建请求。
 * <p>
 * 字段说明如下：
 * <ul>
 *     <li>{@code targetUserId}：仅在系统管理员代预约时传入，普通用户应为空或等于本人。</li>
 *     <li>{@code deviceIds}：本次整单预约的设备 ID 列表，服务端会负责去重、上限、存在性和冲突校验。</li>
 *     <li>{@code startTime}/{@code endTime}：整单共享的预约时间窗，任一设备不满足都必须整单失败。</li>
 *     <li>{@code purpose}/{@code remark}：与旧单预约保持一致的用途和备注字段，避免前端维护两套表单模型。</li>
 * </ul>
 */
public record CreateMultiReservationRequest(
        String targetUserId,
        List<String> deviceIds,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String purpose,
        String remark) {
}
