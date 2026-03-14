package com.jhun.backend.dto.reservation;

/**
 * 人工处理请求。
 * <p>
 * 当预约进入 PENDING_MANUAL 后，由设备管理员执行人工处理：
 * - approved=true：人工确认预约继续有效；
 * - approved=false：人工取消预约。
 */
public record ManualProcessRequest(Boolean approved, String remark) {
}
