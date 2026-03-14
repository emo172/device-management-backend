package com.jhun.backend.dto.statistics;

/**
 * 热门时段统计响应。
 *
 * @param timeSlot 小时段，固定使用两位字符串表示
 * @param totalReservations 预约总数
 * @param approvedReservations 审批通过数
 */
public record TimeSlotStatisticsResponse(String timeSlot, Integer totalReservations, Integer approvedReservations) {
}
