package com.jhun.backend.dto.statistics;

import java.time.LocalDate;

/**
 * 借用统计响应。
 *
 * @param statDate 统计日期
 * @param totalBorrows 借出总数
 * @param totalReturns 归还总数
 */
public record BorrowStatisticsResponse(LocalDate statDate, Integer totalBorrows, Integer totalReturns) {
}
