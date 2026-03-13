package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.service.support.reservation.ConflictDetector;
import com.jhun.backend.service.support.reservation.ReservationValidator;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 预约服务实现。
 * <p>
 * 当前阶段先覆盖预约创建、一审、二审与并发冲突检测，保证预约主链路在 SQL 新口径下可联调可验证。
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ReservationValidator reservationValidator;
    private final ConflictDetector conflictDetector;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, ReentrantLock> deviceLocks = new ConcurrentHashMap<>();

    public ReservationServiceImpl(
            ReservationMapper reservationMapper,
            DeviceMapper deviceMapper,
            DeviceCategoryMapper deviceCategoryMapper,
            NotificationRecordMapper notificationRecordMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            ReservationValidator reservationValidator,
            ConflictDetector conflictDetector,
            TransactionTemplate transactionTemplate) {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.notificationRecordMapper = notificationRecordMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.reservationValidator = reservationValidator;
        this.conflictDetector = conflictDetector;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ReservationResponse createReservation(String userId, String createdBy, CreateReservationRequest request) {
        return createReservationWithMode(userId, createdBy, "SELF", null, request);
    }

    @Override
    public ReservationResponse createReservationWithMode(
            String userId,
            String createdBy,
            String reservationMode,
            String batchId,
            CreateReservationRequest request) {
        reservationValidator.validateCreateRequest(request);
        Device device = mustFindDevice(request.deviceId());
        DeviceCategory category = mustFindCategory(device.getCategoryId());
        ReentrantLock lock = deviceLocks.computeIfAbsent(device.getId(), key -> new ReentrantLock());
        lock.lock();
        try {
            ReservationResponse response = transactionTemplate.execute(status -> {
                List<Reservation> conflicts = reservationMapper.findConflictingReservations(device.getId(), request.startTime(), request.endTime());
                if (conflictDetector.hasConflict(conflicts, request.startTime(), request.endTime())) {
                    throw new BusinessException("预约时间段冲突");
                }
                String approvalMode = reservationValidator.resolveApprovalMode(device, category.getDefaultApprovalMode());
                Reservation reservation = new Reservation();
                reservation.setId(UuidUtil.randomUuid());
                reservation.setBatchId(batchId);
                reservation.setUserId(userId);
                reservation.setCreatedBy(createdBy);
                reservation.setReservationMode(reservationMode);
                reservation.setDeviceId(device.getId());
                reservation.setStartTime(request.startTime());
                reservation.setEndTime(request.endTime());
                reservation.setPurpose(request.purpose());
                reservation.setRemark(request.remark());
                reservation.setApprovalModeSnapshot(approvalMode);
                reservation.setStatus(reservationValidator.resolveInitialStatus(device, category.getDefaultApprovalMode()));
                reservation.setSignStatus("NOT_CHECKED_IN");
                reservationMapper.insert(reservation);
                saveNotification(userId, "FIRST_APPROVAL_TODO", "IN_APP", "预约待审批", "您的预约已提交，等待设备管理员审批", reservation.getId(), "RESERVATION");
                if ("ON_BEHALF".equals(reservationMode)) {
                    saveNotification(
                            userId,
                            "ON_BEHALF_CREATED",
                            "IN_APP",
                            "收到代预约",
                            "系统管理员已为您创建预约，请及时查看审批进度",
                            reservation.getId(),
                            "RESERVATION");
                }
                return toResponse(reservation);
            });
            if (response == null) {
                throw new BusinessException("预约创建失败");
            }
            return response;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ReservationResponse createProxyReservation(String operatorId, String operatorRole, ProxyReservationRequest request) {
        if (!"SYSTEM_ADMIN".equals(operatorRole)) {
            throw new BusinessException("只有系统管理员可以代预约");
        }
        User targetUser = mustFindUser(request.targetUserId());
        Role targetRole = roleMapper.selectById(targetUser.getRoleId());
        if (targetRole == null || !"USER".equals(targetRole.getName())) {
            throw new BusinessException("系统管理员仅可代 USER 预约");
        }
        return createReservationWithMode(
                targetUser.getId(),
                operatorId,
                "ON_BEHALF",
                null,
                new CreateReservationRequest(
                        request.deviceId(),
                        request.startTime(),
                        request.endTime(),
                        request.purpose(),
                        request.remark()));
    }

    @Override
    @Transactional
    public ReservationResponse deviceApprove(String reservationId, String approverId, String role, AuditReservationRequest request) {
        if (!"DEVICE_ADMIN".equals(role)) {
            throw new BusinessException("只有设备管理员可以执行第一审");
        }
        Reservation reservation = mustFindReservation(reservationId);
        if (!"PENDING_DEVICE_APPROVAL".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约不处于待设备审批状态");
        }
        reservation.setDeviceApproverId(approverId);
        reservation.setDeviceApprovedAt(LocalDateTime.now());
        reservation.setDeviceApprovalRemark(request.remark());
        if (Boolean.TRUE.equals(request.approved())) {
            if ("DEVICE_THEN_SYSTEM".equals(reservation.getApprovalModeSnapshot())) {
                reservation.setStatus("PENDING_SYSTEM_APPROVAL");
                saveNotification(reservation.getUserId(), "SECOND_APPROVAL_TODO", "IN_APP", "预约待系统审批", "您的预约已通过第一审，等待系统管理员审批", reservation.getId(), "RESERVATION");
            } else {
                reservation.setStatus("APPROVED");
                saveNotification(reservation.getUserId(), "APPROVAL_PASSED", "IN_APP", "预约审批通过", "您的预约已审批通过", reservation.getId(), "RESERVATION");
            }
        } else {
            reservation.setStatus("REJECTED");
            saveNotification(reservation.getUserId(), "APPROVAL_REJECTED", "IN_APP", "预约审批拒绝", "您的预约被设备管理员拒绝", reservation.getId(), "RESERVATION");
        }
        reservationMapper.updateById(reservation);
        return toResponse(reservation);
    }

    @Override
    @Transactional
    public ReservationResponse systemApprove(String reservationId, String approverId, String role, AuditReservationRequest request) {
        if (!"SYSTEM_ADMIN".equals(role)) {
            throw new BusinessException("只有系统管理员可以执行第二审");
        }
        Reservation reservation = mustFindReservation(reservationId);
        if (!"PENDING_SYSTEM_APPROVAL".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约不处于待系统审批状态");
        }
        if (approverId.equals(reservation.getDeviceApproverId())) {
            throw new BusinessException("同一账号不能完成双审两步");
        }
        reservation.setSystemApproverId(approverId);
        reservation.setSystemApprovedAt(LocalDateTime.now());
        reservation.setSystemApprovalRemark(request.remark());
        reservation.setStatus(Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED");
        reservationMapper.updateById(reservation);
        saveNotification(
                reservation.getUserId(),
                Boolean.TRUE.equals(request.approved()) ? "APPROVAL_PASSED" : "APPROVAL_REJECTED",
                "IN_APP",
                Boolean.TRUE.equals(request.approved()) ? "预约审批通过" : "预约审批拒绝",
                Boolean.TRUE.equals(request.approved()) ? "您的预约已完成全部审批" : "您的预约被系统管理员拒绝",
                reservation.getId(),
                "RESERVATION");
        return toResponse(reservation);
    }

    private Device mustFindDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private DeviceCategory mustFindCategory(String categoryId) {
        DeviceCategory category = deviceCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("设备分类不存在");
        }
        return category;
    }

    private Reservation mustFindReservation(String reservationId) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预约不存在");
        }
        return reservation;
    }

    private User mustFindUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("目标用户不存在");
        }
        return user;
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBatchId(),
                reservation.getUserId(),
                reservation.getCreatedBy(),
                reservation.getReservationMode(),
                reservation.getDeviceId(),
                reservation.getStatus(),
                reservation.getApprovalModeSnapshot(),
                reservation.getDeviceApproverId(),
                reservation.getSystemApproverId());
    }

    private void saveNotification(String userId, String type, String channel, String title, String content, String relatedId, String relatedType) {
        NotificationRecord record = new NotificationRecord();
        record.setId(UuidUtil.randomUuid());
        record.setUserId(userId);
        record.setNotificationType(type);
        record.setChannel(channel);
        record.setTitle(title);
        record.setContent(content);
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setReadFlag(0);
        record.setRelatedId(relatedId);
        record.setRelatedType(relatedType);
        notificationRecordMapper.insert(record);
    }
}
