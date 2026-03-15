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
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.service.DeviceService;
import com.jhun.backend.service.support.device.DeviceImageStorageSupport;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备服务实现。
 * <p>
 * 除设备主数据 CRUD 外，该实现还承担关键状态变更的最小通知闭环，
 * 例如设备进入维修状态时，需要提醒未来已审批预约的受影响用户，避免形成“预约通过但设备不可用”的联调断层。
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final DeviceStatusLogMapper deviceStatusLogMapper;
    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final DeviceImageStorageSupport deviceImageStorageSupport;

    public DeviceServiceImpl(
            DeviceMapper deviceMapper,
            DeviceCategoryMapper deviceCategoryMapper,
            DeviceStatusLogMapper deviceStatusLogMapper,
            ReservationMapper reservationMapper,
            NotificationRecordMapper notificationRecordMapper,
            DeviceImageStorageSupport deviceImageStorageSupport) {
        this.deviceMapper = deviceMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.deviceStatusLogMapper = deviceStatusLogMapper;
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
        this.deviceImageStorageSupport = deviceImageStorageSupport;
    }

    /**
     * 创建设备主数据。
     * <p>
     * 设备编号必须保持全局唯一，否则后续预约、借还和维修通知都会失去稳定的设备定位锚点。
     */
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

    /**
     * 更新设备基础信息。
     * <p>
     * 该方法只处理设备主数据字段，不承担借还闭环中的正式状态回退职责，避免越权绕过借还流程。
     */
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

    /**
     * 软删除设备。
     * <p>
     * 软删除使用业务终态 DELETED 保留历史审计记录，而不是物理删除，避免历史预约和借还记录失去关联对象。
     */
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

    /**
     * 查询设备分页列表。
     * <p>
     * 列表接口保留给已登录用户浏览，因此不在服务层额外收敛角色；
     * 分类筛选只影响展示范围，不改变设备生命周期权限边界。
     */
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

    /**
     * 查询设备详情。
     * <p>
     * 详情会带出设备状态日志，帮助前端同时呈现当前状态与最近状态流转轨迹。
     */
    @Override
    public DeviceDetailResponse getDeviceDetail(String deviceId) {
        Device device = mustFindDevice(deviceId);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toDetailResponse(device, category == null ? null : category.getName());
    }

    /**
     * 上传设备图片。
     * <p>
     * 图片存储成功后立即回写设备记录，确保详情页总能以设备表中的 image_url 作为唯一真相源。
     */
    @Override
    @Transactional
    public DeviceDetailResponse uploadImage(String deviceId, MultipartFile file, String operatorId) {
        Device device = mustFindDevice(deviceId);
        device.setImageUrl(deviceImageStorageSupport.store(deviceId, file));
        deviceMapper.updateById(device);
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toDetailResponse(device, category == null ? null : category.getName());
    }

    /**
     * 更新设备状态。
     * <p>
     * 该方法由 DEVICE_ADMIN 执行正式设备状态流转；
     * 除写入状态日志外，当设备首次进入 MAINTENANCE 时，还会通知未来审批通过预约的受影响用户。
     */
    @Override
    @Transactional
    public DeviceResponse updateDeviceStatus(String deviceId, UpdateDeviceStatusRequest request, String operatorId) {
        Device device = mustFindDevice(deviceId);
        validateStatusTransition(device.getStatus(), request.status());
        String oldStatus = device.getStatus();
        LocalDateTime changeTime = LocalDateTime.now();
        device.setStatus(request.status());
        device.setStatusChangeReason(request.reason());
        deviceMapper.updateById(device);
        saveStatusLog(device.getId(), oldStatus, request.status(), request.reason(), operatorId);
        if (!"MAINTENANCE".equals(oldStatus) && "MAINTENANCE".equals(request.status())) {
            notifyFutureReservationUsersForMaintenance(device, request.reason(), changeTime);
        }
        DeviceCategory category = deviceCategoryMapper.selectById(device.getCategoryId());
        return toResponse(device, category == null ? null : category.getName());
    }

    /**
     * 向未来审批通过预约的用户发送设备维修通知。
     * <p>
     * 这里按用户去重，避免同一用户存在多条未来预约时被重复轰炸；
     * 相关预约明细仍保留在预约模块中，当前通知只负责告知“该设备已经进入维修状态”。
     */
    private void notifyFutureReservationUsersForMaintenance(Device device, String reason, LocalDateTime changeTime) {
        Set<String> notifiedUserIds = reservationMapper.findApprovedFutureReservationsByDeviceId(device.getId(), changeTime)
                .stream()
                .map(Reservation::getUserId)
                .collect(Collectors.toSet());
        for (String userId : notifiedUserIds) {
            NotificationRecord record = new NotificationRecord();
            record.setId(UuidUtil.randomUuid());
            record.setUserId(userId);
            record.setNotificationType("DEVICE_MAINTENANCE_NOTICE");
            record.setChannel("IN_APP");
            record.setTitle("设备进入维修状态");
            record.setContent("您已审批通过的设备“" + device.getName() + "”进入维修状态，原因：" + reason + "，请及时调整预约安排。");
            record.setStatus("SUCCESS");
            record.setRetryCount(0);
            record.setSentAt(changeTime);
            record.setReadFlag(0);
            record.setRelatedId(device.getId());
            record.setRelatedType("DEVICE");
            record.setCreatedAt(changeTime);
            record.setUpdatedAt(changeTime);
            notificationRecordMapper.insert(record);
        }
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
        /*
         * 设备一旦进入 device_status.BORROWED，后续状态回退必须通过借还域的正式归还流程完成。
         * 因此这里不仅禁止直接改回 AVAILABLE，也禁止手工改到 MAINTENANCE、DISABLED 等其他状态，
         * 否则会绕开 borrow_record 的闭环确认，造成“设备状态已变更但借还记录仍未归还”的数据裂缝。
         */
        if ("BORROWED".equals(oldStatus) && !"BORROWED".equals(newStatus)) {
            throw new BusinessException("借出设备不能手工变更状态，必须通过归还流程完成闭环");
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
