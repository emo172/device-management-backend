package com.jhun.backend.dto.reservation;

/**
 * 多设备预约失败时的阻塞设备说明。
 * <p>
 * 前端不应再自行推断“为什么这台设备失败”，而是直接消费后端返回的设备级阻塞原因，
 * 从而把重复、超上限、设备不存在、不可预约、时间冲突和权限不足统一呈现给用户。
 *
 * @param deviceId 阻塞设备 ID；当请求体传入的 ID 本身非法或不存在时，仍回显原始 deviceId 方便前端定位
 * @param deviceName 阻塞设备名称；若设备不存在则允许为空
 * @param reasonCode 失败原因码，仅允许使用计划约定的六种固定值
 * @param reasonMessage 面向调用方的可读失败说明
 */
public record BlockingDeviceResponse(
        String deviceId,
        String deviceName,
        String reasonCode,
        String reasonMessage) {
}
