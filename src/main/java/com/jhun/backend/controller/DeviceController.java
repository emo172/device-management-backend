package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceRequest;
import com.jhun.backend.service.DeviceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备控制器。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public Result<DeviceResponse> create(@RequestBody CreateDeviceRequest request) {
        return Result.success(deviceService.createDevice(request));
    }

    @PutMapping("/{id}")
    public Result<DeviceResponse> update(@PathVariable("id") String deviceId, @RequestBody UpdateDeviceRequest request) {
        return Result.success(deviceService.updateDevice(deviceId, request));
    }

    @DeleteMapping("/{id}")
    public Result<DeviceResponse> delete(@PathVariable("id") String deviceId) {
        return Result.success(deviceService.softDeleteDevice(deviceId));
    }

    @GetMapping
    public Result<DevicePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryName) {
        return Result.success(deviceService.listDevices(page, size, categoryName));
    }
}
