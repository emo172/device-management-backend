package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.DeviceStatusLogResponse;
import com.jhun.backend.dto.device.UpdateDeviceStatusRequest;
import com.jhun.backend.dto.device.UpdateDeviceRequest;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.DeviceStatusLog;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.service.DeviceService;
import com.jhun.backend.service.support.device.DeviceImageStorageSupport;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备服务实现。
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final DeviceStatusLogMapper deviceStatusLogMapper;
    private final DeviceImageStorageSupport deviceImageStorageSupport;

    public DeviceServiceImpl(
            DeviceMapper deviceMapper,
            DeviceCategoryMapper deviceCategoryMapper,
            DeviceStatusLogMapper deviceStatusLogMapper,
            DeviceImageStorageSupport deviceImageStorageSupport) {
        this.deviceMapper = deviceMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.deviceStatusLogMapper = deviceStatusLogMapper;
        this.deviceImageStorageSupport = deviceImageStorageSupport;
    }

    @Override
    @Transactional
    public DeviceResponse createDevice(CreateDeviceRequest request) {
        if (deviceMapper.findByDeviceNumber(request.deviceNumber()) != null) {
            throw new BusinessException("设备编号已存在");
        }
        DeviceCategory category = findCategoryByName(request.categoryName());
        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName(request.name());
        device.setDeviceNumber(request.deviceNumber());
        device.setCategoryId(category.getId());
        device.setStatus(request.status());
        device.setDescription(request.description());
        device.setLocation(request.location());
        deviceMapper.insert(device);
        return toResponse(device, category.getName());
    }

    @Override
    @Transactional
    public DeviceResponse updateDevice(String deviceId, UpdateDeviceRequest request) {
        Device device = mustFindDevice(deviceId);
        DeviceCategory category = findCategoryByName(request.categoryName());
        device.setName(request.name());
        device.setCategoryId(category.getId());
        device.setStatus(request.status());
        device.setDescription(request.description());
        device.setLocation(request.location());
        deviceMapper.updateById(device);
        return toResponse(device, category.getName());
    }

    @Override
    @Transactional
    public DeviceResponse softDeleteDevice(String deviceId) {
        Device device = mustFindDevice(deviceId);
        device.setStatus("DELETED");
        device.setStatusChangeReason("软删除");
        deviceMapper.updateById(device);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toResponse(device, category == null ? null : category.getName());
    }

    @Override
    public DevicePageResponse listDevices(int page, int size, String categoryName) {
        String categoryId = null;
        if (categoryName != null && !categoryName.isBlank()) {
            categoryId = findCategoryByName(categoryName).getId();
        }
        List<Device> devices = deviceMapper.findActiveDevices(categoryId);
        Map<String, String> categoryNameMap = deviceCategoryMapper.findAllOrderBySort().stream()
                .collect(Collectors.toMap(DeviceCategory::getId, DeviceCategory::getName));

        int fromIndex = Math.max((page - 1) * size, 0);
        int toIndex = Math.min(fromIndex + size, devices.size());
        List<DeviceResponse> records = fromIndex >= devices.size()
                ? List.of()
                : devices.subList(fromIndex, toIndex).stream()
                        .map(device -> toResponse(device, categoryNameMap.get(device.getCategoryId())))
                        .toList();
        return new DevicePageResponse(devices.size(), records);
    }

    @Override
    public DeviceDetailResponse getDeviceDetail(String deviceId) {
        Device device = mustFindDevice(deviceId);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toDetailResponse(device, category == null ? null : category.getName());
    }

    @Override
    @Transactional
    public DeviceDetailResponse uploadImage(String deviceId, MultipartFile file, String operatorId) {
        Device device = mustFindDevice(deviceId);
        device.setImageUrl(deviceImageStorageSupport.store(deviceId, file));
        deviceMapper.updateById(device);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toDetailResponse(device, category == null ? null : category.getName());
    }

    @Override
    @Transactional
    public DeviceResponse updateDeviceStatus(String deviceId, UpdateDeviceStatusRequest request, String operatorId) {
        Device device = mustFindDevice(deviceId);
        validateStatusTransition(device.getStatus(), request.status());
        String oldStatus = device.getStatus();
        device.setStatus(request.status());
        device.setStatusChangeReason(request.reason());
        deviceMapper.updateById(device);
        saveStatusLog(device.getId(), oldStatus, request.status(), request.reason(), operatorId);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toResponse(device, category == null ? null : category.getName());
    }

    private DeviceCategory findCategoryByName(String categoryName) {
        DeviceCategory root = deviceCategoryMapper.findRootByName(categoryName);
        if (root != null) {
            return root;
        }
        return deviceCategoryMapper.findAllOrderBySort().stream()
                .filter(category -> category.getName().equals(categoryName))
                .findFirst()
                .orElseThrow(() -> new BusinessException("设备分类不存在"));
    }

    private Device mustFindDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private DeviceResponse toResponse(Device device, String categoryName) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getDeviceNumber(),
                device.getCategoryId(),
                categoryName,
                device.getStatus(),
                device.getDescription(),
                device.getLocation());
    }

    private DeviceDetailResponse toDetailResponse(Device device, String categoryName) {
        List<DeviceStatusLogResponse> statusLogs = deviceStatusLogMapper.findByDeviceId(device.getId()).stream()
                .map(log -> new DeviceStatusLogResponse(log.getOldStatus(), log.getNewStatus(), log.getReason()))
                .toList();
        return new DeviceDetailResponse(
                device.getId(),
                device.getName(),
                device.getDeviceNumber(),
                device.getCategoryId(),
                categoryName,
                device.getStatus(),
                device.getDescription(),
                device.getLocation(),
                device.getImageUrl(),
                statusLogs);
    }

    private void validateStatusTransition(String oldStatus, String newStatus) {
        if ("BORROWED".equals(oldStatus) && "AVAILABLE".equals(newStatus)) {
            throw new BusinessException("借出设备不能直接改回可借，必须通过归还流程完成");
        }
    }

    private void saveStatusLog(String deviceId, String oldStatus, String newStatus, String reason, String operatorId) {
        DeviceStatusLog log = new DeviceStatusLog();
        log.setId(UuidUtil.randomUuid());
        log.setDeviceId(deviceId);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setCreatedAt(LocalDateTime.now());
        deviceStatusLogMapper.insert(log);
    }
}
