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
import com.jhun.backend.mapper.BorrowRecordMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.service.BorrowService;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
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
    private final DeviceMapper deviceMapper;
    private final DeviceStatusLogMapper deviceStatusLogMapper;

    public BorrowServiceImpl(
            BorrowRecordMapper borrowRecordMapper,
            ReservationMapper reservationMapper,
            DeviceMapper deviceMapper,
            DeviceStatusLogMapper deviceStatusLogMapper) {
        this.borrowRecordMapper = borrowRecordMapper;
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.deviceStatusLogMapper = deviceStatusLogMapper;
    }

    @Override
    @Transactional
    /**
     * 以事务方式完成借用确认。
     * <p>
     * 这里先校验角色、预约状态和签到状态，再校验同一预约只能生成一条借还记录，最后同时写入借还记录并更新设备状态与状态日志；
     * 之所以必须放在一个事务里，是因为借用确认一旦成功，系统就要同时承认“记录已建立”和“设备已借出”两个事实，任何一步单独成功都会导致业务状态失真。
     */
    public BorrowRecordResponse confirmBorrow(String reservationId, String operatorId, String role, ConfirmBorrowRequest request) {
        ensureDeviceAdmin(role, "只有设备管理员可以确认借用");
        Reservation reservation = mustFindReservation(reservationId);
        validateBorrowableReservation(reservation);
        if (borrowRecordMapper.findByReservationId(reservationId) != null) {
            throw new BusinessException("同一预约已生成借还记录");
        }

        Device device = mustFindDevice(reservation.getDeviceId());
        if (!"AVAILABLE".equals(device.getStatus())) {
            throw new BusinessException("当前设备状态不允许确认借用");
        }

        LocalDateTime borrowTime = request != null && request.borrowTime() != null ? request.borrowTime() : LocalDateTime.now();
        if (borrowTime.isAfter(reservation.getEndTime())) {
            throw new BusinessException("借用时间不能晚于预约结束时间");
        }

        BorrowRecord borrowRecord = new BorrowRecord();
        borrowRecord.setId(UuidUtil.randomUuid());
        borrowRecord.setReservationId(reservation.getId());
        borrowRecord.setDeviceId(reservation.getDeviceId());
        borrowRecord.setUserId(reservation.getUserId());
        borrowRecord.setBorrowTime(borrowTime);
        borrowRecord.setExpectedReturnTime(reservation.getEndTime());
        borrowRecord.setStatus("BORROWED");
        borrowRecord.setBorrowCheckStatus(request == null ? null : request.borrowCheckStatus());
        borrowRecord.setRemark(request == null ? null : request.remark());
        borrowRecord.setOperatorId(operatorId);
        borrowRecord.setCreatedAt(LocalDateTime.now());
        borrowRecord.setUpdatedAt(LocalDateTime.now());
        borrowRecordMapper.insert(borrowRecord);

        updateDeviceStatus(device, "BORROWED", "借用确认", operatorId);
        return toResponse(borrowRecord);
    }

    @Override
    @Transactional
    /**
     * 以事务方式完成归还确认。
     * <p>
     * 只有设备管理员可以执行该操作，并且只有处于借出中的正式记录才允许归还；
     * 该方法先更新 borrow_record 的归还信息，再把设备从 {@code BORROWED} 恢复为 {@code AVAILABLE} 并记录设备日志，
     * 用于落实“归还必须走正式流程，不能靠设备状态手工回退”的规则。
     */
    public BorrowRecordResponse confirmReturn(String borrowRecordId, String operatorId, String role, ConfirmReturnRequest request) {
        ensureDeviceAdmin(role, "只有设备管理员可以确认归还");
        BorrowRecord borrowRecord = mustFindBorrowRecord(borrowRecordId);
        if (!"BORROWED".equals(borrowRecord.getStatus()) && !"OVERDUE".equals(borrowRecord.getStatus())) {
            throw new BusinessException("当前借还记录不处于可归还状态");
        }

        LocalDateTime returnTime = request != null && request.returnTime() != null ? request.returnTime() : LocalDateTime.now();
        if (returnTime.isBefore(borrowRecord.getBorrowTime())) {
            throw new BusinessException("归还时间不能早于借用时间");
        }

        borrowRecord.setReturnTime(returnTime);
        borrowRecord.setReturnCheckStatus(request == null ? null : request.returnCheckStatus());
        borrowRecord.setReturnOperatorId(operatorId);
        borrowRecord.setStatus("RETURNED");
        borrowRecord.setRemark(mergeReturnRemark(borrowRecord.getRemark(), request == null ? null : request.remark()));
        borrowRecord.setUpdatedAt(LocalDateTime.now());
        borrowRecordMapper.updateById(borrowRecord);

        Device device = mustFindDevice(borrowRecord.getDeviceId());
        if (!"BORROWED".equals(device.getStatus())) {
            throw new BusinessException("设备当前不处于借出状态，无法确认归还");
        }
        updateDeviceStatus(device, "AVAILABLE", "归还确认", operatorId);
        return toResponse(borrowRecord);
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
        List<BorrowRecord> allRecords = borrowRecordMapper.findByConditions(status, visibleUserId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        int toIndex = Math.min(fromIndex + safeSize, allRecords.size());
        List<BorrowRecordResponse> records = fromIndex >= allRecords.size()
                ? List.of()
                : allRecords.subList(fromIndex, toIndex).stream().map(this::toResponse).toList();
        return new BorrowRecordPageResponse(allRecords.size(), records);
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
     * 执行设备状态正式流转并写入状态日志。
     * <p>
     * 借还模块不能只改 device.status，因为那样会丢失是谁、因为什么做了状态切换；
     * 因此每次借出/归还都同步写入 device_status_log，确保后续审计可以完整还原状态演进过程。
     */
    private void updateDeviceStatus(Device device, String newStatus, String reason, String operatorId) {
        String oldStatus = device.getStatus();
        device.setStatus(newStatus);
        device.setStatusChangeReason(reason);
        deviceMapper.updateById(device);
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
        return new BorrowRecordResponse(
                borrowRecord.getId(),
                borrowRecord.getReservationId(),
                borrowRecord.getDeviceId(),
                borrowRecord.getUserId(),
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
        Reservation reservation = reservationMapper.selectById(reservationId);
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
