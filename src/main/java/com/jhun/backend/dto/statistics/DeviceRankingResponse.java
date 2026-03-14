package com.jhun.backend.dto.statistics;

import java.math.BigDecimal;

/**
 * 设备排行榜响应。
 *
 * @param deviceId 设备 ID
 * @param deviceName 设备名称
 * @param totalBorrows 借出总数
 * @param utilizationRate 利用率百分比
 */
public record DeviceRankingResponse(String deviceId, String deviceName, Integer totalBorrows, BigDecimal utilizationRate) {
}
