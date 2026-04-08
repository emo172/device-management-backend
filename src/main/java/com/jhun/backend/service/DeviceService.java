package com.jhun.backend.service;

import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceStatusRequest;
import com.jhun.backend.dto.device.UpdateDeviceRequest;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备服务。
 */
public interface DeviceService {

    DeviceResponse createDevice(CreateDeviceRequest request);

    DeviceResponse updateDevice(String deviceId, UpdateDeviceRequest request);

    DeviceResponse softDeleteDevice(String deviceId);

    DevicePageResponse listDevices(int page, int size, String categoryName);

    DevicePageResponse searchReservableDevices(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String q,
            String categoryId,
            boolean includeDescendants,
            int page,
            int size);

    DeviceDetailResponse getDeviceDetail(String deviceId);

    DeviceDetailResponse uploadImage(String deviceId, MultipartFile file, String operatorId);

    DeviceResponse updateDeviceStatus(String deviceId, UpdateDeviceStatusRequest request, String operatorId);
}
