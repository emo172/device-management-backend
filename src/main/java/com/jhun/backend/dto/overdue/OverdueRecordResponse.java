package com.jhun.backend.dto.overdue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 逾期记录响应。
 * <p>
 * 该响应同时承载列表和详情所需的核心字段，避免前端在最小闭环阶段为“列表字段”和“详情字段”维护两套重复模型。
 * 关键字段说明：
 * 1. {@code overdueHours}/{@code overdueDays} 表示当前已累计的逾期时长快照；
 * 2. {@code processingStatus}/{@code processingMethod} 表示管理员处理进度；
 * 3. {@code notificationSent} 表示 C-06 是否已为该条逾期记录发出正式提醒。
 *
 * @param id 逾期记录 ID
 * @param borrowRecordId 关联借还记录 ID
 * @param userId 逾期用户 ID
 * @param userName 逾期用户展示名；优先使用实名，实名为空时回退用户名
 * @param deviceId 逾期设备 ID
 * @param deviceName 逾期设备名称；用于让前端直接展示真实设备信息而不是自行拼接字段
 * @param deviceNumber 逾期设备编号；供管理端快速定位实物设备
 * @param overdueHours 逾期小时数
 * @param overdueDays 逾期天数
 * @param processingStatus 处理状态
 * @param processingMethod 处理方式
 * @param processingRemark 处理备注
 * @param processorId 处理人 ID
 * @param processingTime 处理时间
 * @param compensationAmount 赔偿金额
 * @param notificationSent 是否已发送逾期提醒
 * @param createdAt 创建时间
 */
public record OverdueRecordResponse(
        String id,
        String borrowRecordId,
        String userId,
        String userName,
        String deviceId,
        String deviceName,
        String deviceNumber,
        Integer overdueHours,
        Integer overdueDays,
        String processingStatus,
        String processingMethod,
        String processingRemark,
        String processorId,
        LocalDateTime processingTime,
        BigDecimal compensationAmount,
        Integer notificationSent,
        LocalDateTime createdAt) {
}
