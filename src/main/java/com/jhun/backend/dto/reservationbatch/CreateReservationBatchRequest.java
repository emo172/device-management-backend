package com.jhun.backend.dto.reservationbatch;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建预约批次请求。
 * <p>
 * - USER：仅允许为自己创建批量预约；
 * - SYSTEM_ADMIN：可指定 targetUserId 为 USER 发起管理型批量预约。
 */
public record CreateReservationBatchRequest(
        String targetUserId,
        List<BatchReservationItem> items) {

    /**
     * 批次中的单条预约条目。
     */
    public record BatchReservationItem(
            String deviceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String purpose,
            String remark) {
    }
}
