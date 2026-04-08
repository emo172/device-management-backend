package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.borrow.BorrowRecordPageResponse;
import com.jhun.backend.dto.borrow.BorrowRecordResponse;
import com.jhun.backend.dto.borrow.ConfirmBorrowRequest;
import com.jhun.backend.dto.borrow.ConfirmReturnRequest;
import com.jhun.backend.entity.BorrowRecord;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceStatusLog;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.ReservationDevice;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.BorrowRecordMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.ReservationDeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.BorrowService;
import com.jhun.backend.util.UuidUtil;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 借还服务实现。
 * <p>
 * 当前阶段聚焦 Task 11：
 * 一是把“已审批且已签到”的预约转换为正式借还记录；
 * 二是确保归还确认前设备不能直接回到 AVAILABLE；
 * 三是保证 borrow_record、device、device_status_log 在同一事务内提交，避免出现部分成功的审计裂缝。
 */
@Service
public class BorrowServiceImpl implements BorrowService {

    private final BorrowRecordMapper borrowRecordMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationDeviceMapper reservationDeviceMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceStatusLogMapper deviceStatusLogMapper;
    private final UserMapper userMapper;

    public BorrowServiceImpl(
            BorrowRecordMapper borrowRecordMapper,
            ReservationMapper reservationMapper,
            ReservationDeviceMapper reservationDeviceMapper,
            DeviceMapper deviceMapper,
            DeviceStatusLogMapper deviceStatusLogMapper,
            UserMapper userMapper) {
        this.borrowRecordMapper = borrowRecordMapper;
        this.reservationMapper = reservationMapper;
        this.reservationDeviceMapper = reservationDeviceMapper;
        this.deviceMapper = deviceMapper;
        this.deviceStatusLogMapper = deviceStatusLogMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    /**
     * 以事务方式完成借用确认。
     * <p>
     * 这里先校验角色、预约状态和签到状态，再确认整单预约尚未生成 borrow_record，随后按预约关联顺序为每台设备各写 1 条正式记录；
     * 之所以必须放在一个事务里，是因为借用确认一旦成功，系统就要同时承认“整单记录已建立”和“整组设备已借出”两个事实，任何一步单独成功都会导致多设备预约出现局部成功分叉。
     */
    public BorrowRecordResponse confirmBorrow(String reservationId, String operatorId, String role, ConfirmBorrowRequest request) {
        ensureDeviceAdmin(role, "只有设备管理员可以确认借用");
        Reservation reservation = mustFindReservation(reservationId);
        validateBorrowableReservation(reservation);
        if (!borrowRecordMapper.findByReservationId(reservationId).isEmpty()) {
            throw new BusinessException("同一预约已生成借还记录");
        }

        List<String> reservationDeviceIds = loadReservationDeviceIds(reservation);
        List<Device> reservationDevices = reservationDeviceIds.stream().map(this::mustFindDevice).toList();
        validateAggregateBorrowableDevices(reservationDevices);

        LocalDateTime borrowTime = request != null && request.borrowTime() != null ? request.borrowTime() : LocalDateTime.now();
        if (borrowTime.isAfter(reservation.getEndTime())) {
            throw new BusinessException("借用时间不能晚于预约结束时间");
        }

        List<BorrowRecord> aggregateBorrowRecords = new ArrayList<>();
        LocalDateTime writeTime = LocalDateTime.now();
        try {
            /*
             * 多设备预约仍然只能整单借出：
             * 这里先按预约关联顺序为每台设备各写 1 条 borrow_record，后续任何一步失败都会由事务整体回滚，
             * 从而避免出现“部分设备已有借还记录、部分设备还停留在 APPROVED”的分叉状态。
             */
            for (String deviceId : reservationDeviceIds) {
                BorrowRecord borrowRecord = buildBorrowRecord(
                        reservation,
                        deviceId,
                        operatorId,
                        request,
                        borrowTime,
                        writeTime);
                borrowRecordMapper.insert(borrowRecord);
                aggregateBorrowRecords.add(borrowRecord);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("同一预约已生成借还记录");
        }

        for (Device device : reservationDevices) {
            updateDeviceStatus(device, "AVAILABLE", "BORROWED", "借用确认", operatorId);
        }
        return toResponse(aggregateBorrowRecords.getFirst());
    }

    @Override
    @Transactional
    /**
     * 以事务方式完成归还确认。
     * <p>
     * 只有设备管理员可以执行该操作，并且一次只能把整张预约聚合里的借还记录一起归还；
     * 该方法会先校验同 reservation_id 下的全部 borrow_record，再统一更新归还信息并把整组设备从 {@code BORROWED} 恢复为 {@code AVAILABLE}，
     * 用于落实“归还必须走正式流程，且不能把多设备预约拆成局部归还”的规则。
     */
    public BorrowRecordResponse confirmReturn(String borrowRecordId, String operatorId, String role, ConfirmReturnRequest request) {
        ensureDeviceAdmin(role, "只有设备管理员可以确认归还");
        BorrowRecord targetBorrowRecord = mustFindBorrowRecord(borrowRecordId);
        List<BorrowRecord> aggregateBorrowRecords = borrowRecordMapper.findByReservationId(targetBorrowRecord.getReservationId());
        if (aggregateBorrowRecords.isEmpty()) {
            throw new BusinessException("借还记录不存在");
        }

        LocalDateTime returnTime = request != null && request.returnTime() != null ? request.returnTime() : LocalDateTime.now();
        validateAggregateReturnableRecords(aggregateBorrowRecords, returnTime);

        List<Device> borrowedDevices = aggregateBorrowRecords.stream()
                .map(record -> mustFindDevice(record.getDeviceId()))
                .toList();
        for (Device device : borrowedDevices) {
            if (!"BORROWED".equals(device.getStatus())) {
                throw new BusinessException("设备当前不处于借出状态，无法确认归还");
            }
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        for (BorrowRecord aggregateBorrowRecord : aggregateBorrowRecords) {
            String mergedRemark = mergeReturnRemark(aggregateBorrowRecord.getRemark(), request == null ? null : request.remark());
            int updatedRows = borrowRecordMapper.updateReturnIfInBorrowedState(
                    aggregateBorrowRecord.getId(),
                    returnTime,
                    request == null ? null : request.returnCheckStatus(),
                    mergedRemark,
                    operatorId,
                    updatedAt);
            if (updatedRows != 1) {
                throw new BusinessException("当前借还记录不处于可归还状态");
            }
            aggregateBorrowRecord.setReturnTime(returnTime);
            aggregateBorrowRecord.setReturnCheckStatus(request == null ? null : request.returnCheckStatus());
            aggregateBorrowRecord.setReturnOperatorId(operatorId);
            aggregateBorrowRecord.setStatus("RETURNED");
            aggregateBorrowRecord.setRemark(mergedRemark);
            aggregateBorrowRecord.setUpdatedAt(updatedAt);
        }

        for (Device device : borrowedDevices) {
            updateDeviceStatus(device, "BORROWED", "AVAILABLE", "归还确认", operatorId);
        }
        return toResponse(selectBorrowRecord(aggregateBorrowRecords, borrowRecordId));
    }

    @Override
    /**
     * 分页查询借还记录。
     * <p>
     * 当调用者是普通用户时，这里强制把查询范围收敛到本人，避免用户通过列表接口浏览他人借用轨迹；
     * 当调用者是管理角色时，则保留管理端排查与审计所需的全量视角。
     */
    public BorrowRecordPageResponse listBorrowRecords(String userId, String role, int page, int size, String status) {
        String visibleUserId = "USER".equals(role) ? userId : null;
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = borrowRecordMapper.countByConditions(status, visibleUserId);
        List<BorrowRecord> borrowRecords = borrowRecordMapper.findPageByConditions(status, visibleUserId, safeSize, offset);
        Map<String, Device> deviceMap = loadDeviceMap(borrowRecords.stream().map(BorrowRecord::getDeviceId).toList());
        Map<String, User> userMap = loadUserMap(borrowRecords.stream().map(BorrowRecord::getUserId).toList());
        List<BorrowRecordResponse> records = borrowRecords
                .stream()
                .map(borrowRecord -> toResponse(
                        borrowRecord,
                        deviceMap.get(borrowRecord.getDeviceId()),
                        userMap.get(borrowRecord.getUserId())))
                .toList();
        return new BorrowRecordPageResponse(total, records);
    }

    @Override
    /**
     * 查询单条借还记录详情。
     * <p>
     * 详情接口与列表接口保持一致的权限边界：普通用户只能查看本人记录，管理角色可以查看全部；
     * 这样可以防止详情接口成为绕过列表过滤的越权入口。
     */
    public BorrowRecordResponse getBorrowRecordDetail(String borrowRecordId, String userId, String role) {
        BorrowRecord borrowRecord = mustFindBorrowRecord(borrowRecordId);
        if ("USER".equals(role) && !userId.equals(borrowRecord.getUserId())) {
            throw new BusinessException("只能查看本人借还记录");
        }
        return toResponse(borrowRecord);
    }

    /**
     * 借用确认前必须同时满足“预约已审批通过”和“用户已完成正常/超时签到”。
     * 这是借还域与预约域的关键衔接点，用于避免管理员绕过签到直接生成正式借还记录。
     */
    private void validateBorrowableReservation(Reservation reservation) {
        if (!"APPROVED".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约状态不允许确认借用");
        }
        if (!"CHECKED_IN".equals(reservation.getSignStatus()) && !"CHECKED_IN_TIMEOUT".equals(reservation.getSignStatus())) {
            throw new BusinessException("预约未签到，不能确认借用");
        }
    }

    /**
     * 校验整单关联设备都仍可借出。
     * <p>
     * 多设备预约不能出现“有的设备已被借走、有的设备还能借”的局部成功，
     * 所以这里必须在真正写 borrow_record 之前先把整组设备状态统一收口为 AVAILABLE。
     */
    private void validateAggregateBorrowableDevices(List<Device> reservationDevices) {
        for (Device device : reservationDevices) {
            if (!"AVAILABLE".equals(device.getStatus())) {
                throw new BusinessException("当前设备状态不允许确认借用");
            }
        }
    }

    /**
     * 校验整单借还记录都仍处于可归还状态。
     * <p>
     * 归还入口虽然仍接收单条 borrowRecordId，但服务层必须把它提升为 reservation 聚合级别处理；
     * 只要任一兄弟记录已经闭环或时间非法，就整单拒绝，避免把多设备预约拆成局部归还。
     */
    private void validateAggregateReturnableRecords(List<BorrowRecord> aggregateBorrowRecords, LocalDateTime returnTime) {
        for (BorrowRecord borrowRecord : aggregateBorrowRecords) {
            if (!"BORROWED".equals(borrowRecord.getStatus()) && !"OVERDUE".equals(borrowRecord.getStatus())) {
                throw new BusinessException("当前借还记录不处于可归还状态");
            }
            if (returnTime.isBefore(borrowRecord.getBorrowTime())) {
                throw new BusinessException("归还时间不能早于借用时间");
            }
        }
    }

    /**
     * 读取预约聚合当前应参与借还流程的设备顺序。
     * <p>
     * 正式真相优先来自 reservation_device；只有极少量尚未完成回填的历史旧数据，
     * 才允许回退到 reservation.device_id 兼容列。
     */
    private List<String> loadReservationDeviceIds(Reservation reservation) {
        List<String> relationDeviceIds = reservationDeviceMapper.findByReservationId(reservation.getId()).stream()
                .map(ReservationDevice::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!relationDeviceIds.isEmpty()) {
            return relationDeviceIds;
        }
        if (reservation.getDeviceId() != null && !reservation.getDeviceId().isBlank()) {
            return List.of(reservation.getDeviceId());
        }
        throw new BusinessException("预约未关联任何设备，不能继续借还流程");
    }

    /**
     * 构造单台设备对应的正式借还记录。
     * <p>
     * 多设备预约内部虽然会扇出多条记录，但共享同一个 reservation_id、borrow_time 和 expected_return_time，
     * 从而保证后续逾期识别和整单归还仍围绕同一预约聚合推进。
     */
    private BorrowRecord buildBorrowRecord(
            Reservation reservation,
            String deviceId,
            String operatorId,
            ConfirmBorrowRequest request,
            LocalDateTime borrowTime,
            LocalDateTime writeTime) {
        BorrowRecord borrowRecord = new BorrowRecord();
        borrowRecord.setId(UuidUtil.randomUuid());
        borrowRecord.setReservationId(reservation.getId());
        borrowRecord.setDeviceId(deviceId);
        borrowRecord.setUserId(reservation.getUserId());
        borrowRecord.setBorrowTime(borrowTime);
        borrowRecord.setExpectedReturnTime(reservation.getEndTime());
        borrowRecord.setStatus("BORROWED");
        borrowRecord.setBorrowCheckStatus(request == null ? null : request.borrowCheckStatus());
        borrowRecord.setRemark(request == null ? null : request.remark());
        borrowRecord.setOperatorId(operatorId);
        borrowRecord.setCreatedAt(writeTime);
        borrowRecord.setUpdatedAt(writeTime);
        return borrowRecord;
    }

    /**
     * 从整单 borrow_record 集合中找回当前入口命中的那条记录。
     * <p>
     * 控制器与 DTO 契约暂未扩展成“返回整组借还记录”，
     * 因此当前仍回传调用入口对应的那条记录，但底层实际完成的是整单借还事务。
     */
    private BorrowRecord selectBorrowRecord(List<BorrowRecord> aggregateBorrowRecords, String borrowRecordId) {
        return aggregateBorrowRecords.stream()
                .filter(record -> borrowRecordId.equals(record.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("借还记录不存在"));
    }

    /**
     * 执行设备状态正式流转并写入状态日志。
     * <p>
     * 借还模块不能只改 device.status，因为那样会丢失是谁、因为什么做了状态切换；
     * 因此每次借出/归还都同步写入 device_status_log，确保后续审计可以完整还原状态演进过程。
     */
    private void updateDeviceStatus(Device device, String expectedStatus, String newStatus, String reason, String operatorId) {
        String oldStatus = device.getStatus();
        int updatedRows = deviceMapper.updateStatusIfCurrent(device.getId(), expectedStatus, newStatus, reason);
        if (updatedRows != 1) {
            throw new BusinessException("设备状态已变化，请刷新后重试");
        }
        device.setStatus(newStatus);
        device.setStatusChangeReason(reason);
        saveStatusLog(device.getId(), oldStatus, newStatus, reason, operatorId);
    }

    /**
     * 归还备注追加在原借出备注之后，避免归还确认覆盖借出交接信息，破坏同一条借还记录的审计完整性。
     */
    private String mergeReturnRemark(String existingRemark, String returnRemark) {
        if (returnRemark == null || returnRemark.isBlank()) {
            return existingRemark;
        }
        if (existingRemark == null || existingRemark.isBlank()) {
            return returnRemark;
        }
        return existingRemark + " | 归还备注：" + returnRemark;
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

    private BorrowRecordResponse toResponse(BorrowRecord borrowRecord) {
        Device device = deviceMapper.selectById(borrowRecord.getDeviceId());
        User user = userMapper.selectById(borrowRecord.getUserId());
        return toResponse(borrowRecord, device, user);
    }

    private BorrowRecordResponse toResponse(BorrowRecord borrowRecord, Device device, User user) {
        return new BorrowRecordResponse(
                borrowRecord.getId(),
                borrowRecord.getReservationId(),
                borrowRecord.getDeviceId(),
                device == null ? null : device.getName(),
                device == null ? null : device.getDeviceNumber(),
                borrowRecord.getUserId(),
                resolveUserName(user),
                borrowRecord.getBorrowTime(),
                borrowRecord.getReturnTime(),
                borrowRecord.getExpectedReturnTime(),
                borrowRecord.getStatus(),
                borrowRecord.getBorrowCheckStatus(),
                borrowRecord.getReturnCheckStatus(),
                borrowRecord.getRemark(),
                borrowRecord.getOperatorId(),
                borrowRecord.getReturnOperatorId());
    }

    private Map<String, Device> loadDeviceMap(List<String> deviceIds) {
        List<String> distinctIds = deviceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return deviceMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));
    }

    private Map<String, User> loadUserMap(List<String> userIds) {
        List<String> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * 借还记录页优先展示实名，只有实名为空时才回退用户名，避免前端再次复制同一套展示兜底逻辑。
     */
    private String resolveUserName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    /**
     * 校验并返回借还记录。
     * <p>
     * 单独抽出该入口是为了把“记录不存在”统一收敛为业务异常，避免控制层或查询接口出现空指针式的非业务失败。
     */
    private BorrowRecord mustFindBorrowRecord(String borrowRecordId) {
        BorrowRecord borrowRecord = borrowRecordMapper.selectById(borrowRecordId);
        if (borrowRecord == null) {
            throw new BusinessException("借还记录不存在");
        }
        return borrowRecord;
    }

    /**
     * 校验并返回预约。
     * <p>
     * 借用确认依赖预约作为真相源，因此这里必须在业务层明确阻断不存在的预约，防止后续写入孤儿 borrow_record。
     */
    private Reservation mustFindReservation(String reservationId) {
        Reservation reservation = reservationMapper.findAggregateById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预约不存在");
        }
        return reservation;
    }

    /**
     * 校验并返回设备。
     * <p>
     * 借还闭环最终要作用到设备状态，所以在进入状态流转前必须保证设备实体真实存在，避免形成无主日志或无效状态更新。
     */
    private Device mustFindDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    /**
     * 强制校验当前操作人是否为设备管理员。
     * <p>
     * 借用确认与归还确认都属于 DEVICE_ADMIN 的专属职责，SYSTEM_ADMIN 不能介入借还状态流转，
     * 因此这里统一收敛角色校验，防止不同入口出现口径不一致。
     */
    private void ensureDeviceAdmin(String role, String message) {
        if (!"DEVICE_ADMIN".equals(role)) {
            throw new BusinessException(message);
        }
    }
}
