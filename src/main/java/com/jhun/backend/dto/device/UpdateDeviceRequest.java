package com.jhun.backend.dto.device;

/**
 * 更新设备请求。
 * <p>
 * 该请求仍保留 `status` 字段用于兼容前端当前表单回填，但通用编辑接口不会据此改变设备生命周期状态；
 * 真实状态流转必须走 `/api/devices/{id}/status`，否则会绕过借还闭环与维修通知逻辑。
 */
public record UpdateDeviceRequest(
        String name,
        String categoryName,
        String status,
        String description,
        String location) {
}
