package com.jhun.backend.service;

import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceStatusRequest;
import com.jhun.backend.dto.device.UpdateDeviceRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备服务。
 */
public interface DeviceService {

    DeviceResponse createDevice(CreateDeviceRequest request);

    DeviceResponse updateDevice(String deviceId, UpdateDeviceRequest request);

    DeviceResponse softDeleteDevice(String deviceId);

    DevicePageResponse listDevices(int page, int size, String categoryName);

    DeviceDetailResponse getDeviceDetail(String deviceId);

    DeviceDetailResponse uploadImage(String deviceId, MultipartFile file, String operatorId);

    DeviceResponse updateDeviceStatus(String deviceId, UpdateDeviceStatusRequest request, String operatorId);
}
