package com.jhun.backend.dto.reservation;

import java.util.List;

/**
 * 多设备预约失败响应。
 * <p>
 * 该 DTO 与 HTTP 409 配套使用，承载整单失败时的设备级阻塞原因列表，
 * 确保调用方无需额外请求或本地猜测即可展示失败明细。
 *
 * @param blockingDevices 阻塞本次整单提交的设备清单
 */
public record MultiReservationConflictResponse(List<BlockingDeviceResponse> blockingDevices) {
}
