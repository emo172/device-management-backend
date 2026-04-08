package com.jhun.backend.dto.reservation;

import java.util.List;

/**
 * 预约分页响应。
 *
 * @param total 当前筛选条件下可见预约总数；普通用户只统计本人记录，管理角色统计管理视角记录
 * @param records 当前页预约记录；每条记录都同时带有主设备兼容字段与多设备摘要字段
 */
public record ReservationPageResponse(long total, List<ReservationListItemResponse> records) {
}
