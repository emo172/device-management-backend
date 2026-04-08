package com.jhun.backend.dto.reservation;

/**
 * 多设备单预约创建成功响应。
 * <p>
 * T3 阶段只新增最小必要的多设备创建契约：
 * 一方面继续复用现有 {@link ReservationResponse} 的工作流上下文，保证旧单设备详情字段仍可直接展示主设备；
 * 另一方面额外返回 {@code deviceCount}，明确告诉前端这是多设备预约而不是单设备预约。
 * 完整的设备列表与兼容读模型扩展会在后续 T4 统一补齐。
 *
 * @param reservation 现有预约工作流回包，当前仍以主设备兼容字段承载摘要信息
 * @param deviceCount 本次整单预约包含的设备数量
 */
public record MultiReservationResponse(ReservationResponse reservation, int deviceCount) {
}
