package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceStatusRequest;
import com.jhun.backend.service.CategoryService;
import com.jhun.backend.service.DeviceService;
import com.jhun.backend.dto.category.CreateCategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 设备服务单元测试。
 * <p>
 * 用于锁定设备状态机边界，特别是禁止绕过归还流程直接把 BORROWED 设备手工改回 AVAILABLE。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeviceServiceTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CategoryService categoryService;

    /**
     * 验证 BORROWED 设备不能直接手工改回 AVAILABLE，保护借还流程唯一出口约束。
     */
    @Test
    void shouldRejectDirectBorrowedToAvailableStatusTransition() {
        categoryService.createCategory(new CreateCategoryRequest("状态机分类", null, 1, "状态机测试", "DEVICE_ONLY"));
        DeviceResponse device = deviceService.createDevice(new CreateDeviceRequest(
                "测试摄像机",
                "DEV-STATE-001",
                "状态机分类",
                "BORROWED",
                "借用中的设备",
                "库房"));

        assertThrows(BusinessException.class, () -> deviceService.updateDeviceStatus(
                device.id(),
                new UpdateDeviceStatusRequest("AVAILABLE", "尝试直接恢复可借"),
                "system-admin-id"));
    }

    /**
     * 验证允许把 AVAILABLE 设备切换为 MAINTENANCE，并正确返回新状态。
     */
    @Test
    void shouldAllowAvailableToMaintenanceStatusTransition() {
        categoryService.createCategory(new CreateCategoryRequest("状态机分类二", null, 1, "状态机测试", "DEVICE_ONLY"));
        DeviceResponse device = deviceService.createDevice(new CreateDeviceRequest(
                "测试投影仪",
                "DEV-STATE-002",
                "状态机分类二",
                "AVAILABLE",
                "可借设备",
                "教室"));

        DeviceResponse updated = deviceService.updateDeviceStatus(
                device.id(),
                new UpdateDeviceStatusRequest("MAINTENANCE", "灯泡检修"),
                "system-admin-id");

        assertEquals("MAINTENANCE", updated.status());
    }
}
