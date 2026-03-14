package com.jhun.backend.dto.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统计总览响应。
 *
 * @param statDate 统计日期
 * @param totalReservations 预约总数
 * @param approvedReservations 审批通过数
 * @param rejectedReservations 审批拒绝数
 * @param cancelledReservations 取消数
 * @param expiredReservations 过期数
 * @param totalBorrows 借出总数
 * @param totalReturns 归还总数
 * @param totalOverdue 逾期总数
 * @param totalOverdueHours 逾期总时长（小时）
 * @param utilizationRate 全局利用率百分比
 */
public record StatisticsOverviewResponse(
        LocalDate statDate,
        Integer totalReservations,
        Integer approvedReservations,
        Integer rejectedReservations,
        Integer cancelledReservations,
        Integer expiredReservations,
        Integer totalBorrows,
        Integer totalReturns,
        Integer totalOverdue,
        Integer totalOverdueHours,
        BigDecimal utilizationRate) {
}
