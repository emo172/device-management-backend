package com.jhun.backend.dto.statistics;

import java.math.BigDecimal;

/**
 * 设备利用率响应。
 *
 * @param deviceId 设备 ID
 * @param deviceName 设备名称
 * @param categoryId 分类 ID
 * @param categoryName 分类名称
 * @param totalReservations 预约总数
 * @param totalBorrows 借出总数
 * @param totalReturns 归还总数
 * @param totalOverdue 逾期数
 * @param utilizationRate 利用率百分比
 */
public record DeviceUtilizationResponse(
        String deviceId,
        String deviceName,
        String categoryId,
        String categoryName,
        Integer totalReservations,
        Integer totalBorrows,
        Integer totalReturns,
        Integer totalOverdue,
        BigDecimal utilizationRate) {
}
