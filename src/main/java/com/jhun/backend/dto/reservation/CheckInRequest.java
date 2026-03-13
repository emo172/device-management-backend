package com.jhun.backend.dto.reservation;

import java.time.LocalDateTime;

/**
 * 签到请求。
 * <p>
 * checkInTime 用于显式传入签到时间，便于测试覆盖签到窗口边界；
 * 为空时按服务端当前时间处理。
 */
public record CheckInRequest(LocalDateTime checkInTime) {
}
