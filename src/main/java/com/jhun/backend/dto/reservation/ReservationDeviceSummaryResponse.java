package com.jhun.backend.dto.reservation;

/**
 * 预约设备摘要响应。
 * <p>
 * `devices[]` 只暴露预约读模型稳定依赖的设备主键、名称和编号，
 * 避免把设备域的可变状态直接耦合进列表、详情与动作回包的兼容契约里。
 *
 * @param deviceId 设备 ID
 * @param deviceName 设备名称
 * @param deviceNumber 设备编号
 */
public record ReservationDeviceSummaryResponse(
        String deviceId,
        String deviceName,
        String deviceNumber) {
}
