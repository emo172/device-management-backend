package com.jhun.backend.dto.device;

/**
 * 设备响应。
 * <p>
 * 列表与写接口共用该 DTO，除基础档案外额外透出图片公开地址，
 * 让设备列表卡片与详情页都能直接消费同一条 `/files/**` 图片路径。
 */
public record DeviceResponse(
        String id,
        String name,
        String deviceNumber,
        String categoryId,
        String categoryName,
        String status,
        String description,
        String location,
        String imageUrl) {
}
