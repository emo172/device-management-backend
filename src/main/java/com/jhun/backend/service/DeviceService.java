package com.jhun.backend.service;

import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceRequest;

/**
 * 设备服务。
 */
public interface DeviceService {

    DeviceResponse createDevice(CreateDeviceRequest request);

    DeviceResponse updateDevice(String deviceId, UpdateDeviceRequest request);

    DeviceResponse softDeleteDevice(String deviceId);

    DevicePageResponse listDevices(int page, int size, String categoryName);
}
