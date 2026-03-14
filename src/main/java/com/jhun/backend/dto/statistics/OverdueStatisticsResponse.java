package com.jhun.backend.dto.statistics;

import java.time.LocalDate;

/**
 * 逾期统计响应。
 *
 * @param statDate 统计日期
 * @param totalOverdue 逾期总数
 * @param totalOverdueHours 逾期总时长（小时）
 */
public record OverdueStatisticsResponse(LocalDate statDate, Integer totalOverdue, Integer totalOverdueHours) {
}
