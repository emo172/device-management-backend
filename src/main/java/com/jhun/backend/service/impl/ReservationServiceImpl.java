package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.common.exception.MultiReservationConflictException;
import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.BlockingDeviceResponse;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.CreateMultiReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ManualProcessRequest;
import com.jhun.backend.dto.reservation.MultiReservationConflictResponse;
import com.jhun.backend.dto.reservation.MultiReservationResponse;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationDeviceSummaryResponse;
import com.jhun.backend.dto.reservation.ReservationListItemResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.ReservationDevice;
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
import com.jhun.backend.service.support.reservation.ReservationDeviceBackfillSupport;
import com.jhun.backend.service.support.reservation.ReservationValidator;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 预约服务实现。
 * <p>
 * 当前阶段覆盖预约创建、列表、详情、取消、一审、二审与并发冲突检测，保证预约主链路既能完成审批流转，
 * 也能按角色边界支撑预约管理页和个人预约页联调。
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    /**
     * 预约取消允许命中的活动状态集合。
     * <p>
     * 只有这些仍可能占用时间窗口、且尚未进入终态的预约才允许继续尝试取消；
     * 具体是否真的能取消，还要再叠加签到状态与开始时间窗口校验。
     */
    private static final List<String> CANCELLABLE_ACTIVE_STATUSES = List.of(
            "PENDING_DEVICE_APPROVAL",
            "PENDING_SYSTEM_APPROVAL",
            "PENDING_MANUAL",
            "APPROVED");

    /** 单次多设备预约允许选择的最大设备数。 */
    private static final int MAX_MULTI_RESERVATION_DEVICE_COUNT = 10;

    private final ReservationMapper reservationMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ReservationValidator reservationValidator;
    private final ReservationDeviceBackfillSupport reservationDeviceBackfillSupport;
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
            ReservationDeviceBackfillSupport reservationDeviceBackfillSupport,
            ConflictDetector conflictDetector,
            TransactionTemplate transactionTemplate) {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.notificationRecordMapper = notificationRecordMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.reservationValidator = reservationValidator;
        this.reservationDeviceBackfillSupport = reservationDeviceBackfillSupport;
        this.conflictDetector = conflictDetector;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 查询预约列表。
     * <p>
     * 仅允许 USER、DEVICE_ADMIN、SYSTEM_ADMIN 三角色访问；USER 只能看到本人预约，管理角色保留管理视角的全量列表。
     * 这里统一在服务层执行角色白名单校验与可见范围裁决，避免出现“非 USER 即管理视角”的越权漏洞。
     */
    @Override
    public ReservationPageResponse listReservations(String userId, String role, int page, int size) {
        ensureSupportedReservationViewerRole(role);
        String visibleUserId = "USER".equals(role) ? userId : null;
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = reservationMapper.countByConditions(visibleUserId);
        List<Reservation> reservations = reservationMapper.findPageByConditions(visibleUserId, safeSize, offset);
        Map<String, List<ReservationDevice>> relationMap = loadReservationDeviceRelationMap(
                reservations.stream().map(Reservation::getId).toList());
        Map<String, Device> deviceMap = loadDeviceMap(collectReadModelDeviceIds(reservations, relationMap));
        Map<String, User> userMap = loadUserMap(reservations.stream()
                .flatMap(reservation -> java.util.stream.Stream.of(reservation.getUserId(), reservation.getCreatedBy()))
                .filter(Objects::nonNull)
                .toList());
        List<ReservationListItemResponse> records = reservations.stream()
                .map(reservation -> toListItemResponse(
                        reservation,
                        relationMap.getOrDefault(reservation.getId(), List.of()),
                        deviceMap,
                        userMap))
                .toList();
        return new ReservationPageResponse(total, records);
    }

    /**
     * 查询预约详情。
     * <p>
     * 详情口径与列表一致：仅允许三角色访问，普通用户只能查看本人预约，管理角色可以查看管理视角详情。
     */
    @Override
    public ReservationDetailResponse getReservationDetail(String reservationId, String userId, String role) {
        ensureSupportedReservationViewerRole(role);
        Reservation reservation = mustFindReservation(reservationId);
        ensureReservationVisible(reservation, userId, role);
        return toDetailResponse(reservation);
    }

    /**
     * 取消预约。
     * <p>
     * 普通用户只能在开始前超过 24 小时取消本人预约；管理角色可以处理 24 小时内但尚未开始的预约；
     * 一旦预约已经开始或已经完成签到，则任何角色都不能通过该接口直接取消，以避免破坏签到与借还链路的时间真相。
     */
    @Override
    @Transactional
    public ReservationDetailResponse cancelReservation(String reservationId, String operatorId, String role, CancelReservationRequest request) {
        ensureSupportedReservationViewerRole(role);
        Reservation reservation = mustFindReservation(reservationId);
        ensureReservationVisible(reservation, operatorId, role);
        LocalDateTime now = LocalDateTime.now();
        ensureCancelable(reservation, operatorId, role, now);
        String cancelReason = request == null ? null : request.reason();
        int updatedRows = reservationMapper.cancelReservationSafely(
                reservationId,
                cancelReason,
                now,
                now,
                now,
                CANCELLABLE_ACTIVE_STATUSES);
        if (updatedRows == 0) {
            throw new BusinessException("预约状态已变化，请刷新后重试");
        }
        reservation.setStatus("CANCELLED");
        reservation.setCancelReason(cancelReason);
        reservation.setCancelTime(now);
        reservation.setUpdatedAt(now);
        reservation.setSignStatus("NOT_CHECKED_IN");
        reservation.setCheckedInAt(null);
        saveNotification(reservation.getUserId(), "RESERVATION_CANCELLED", "IN_APP", "预约已取消", "预约已按规则取消", reservation.getId(), "RESERVATION");
        return toDetailResponse(reservation);
    }

    /**
     * 创建本人预约。
     * <p>
     * 本人预约统一固定为 `SELF` 模式，并把创建人同时记录为当前登录用户，
     * 避免普通用户绕过控制层约束把自己伪装成代预约操作人。
     */
    @Override
    public ReservationResponse createReservation(String userId, String createdBy, CreateReservationRequest request) {
        return createReservationWithMode(userId, createdBy, "SELF", null, request);
    }

    /**
     * 创建多设备单预约。
     * <p>
     * 该入口必须保持“整单原子”语义：
     * 1) 先对请求形状、角色边界、设备存在性与静态可预约状态做整单校验；
     * 2) 再按设备 ID 排序加锁，避免并发多设备请求交叉持锁导致死锁；
     * 3) 最后在同一事务内完成冲突校验、reservation 主表写入和 reservation_device 多行写入。
     * 任一设备失败都通过 409 + blockingDevices[] 返回，并且整单不留下任何半成品数据。
     */
    @Override
    public MultiReservationResponse createMultiReservation(String operatorId, String operatorRole, CreateMultiReservationRequest request) {
        reservationValidator.validateCreateTimeRange(request.startTime(), request.endTime());
        List<String> requestedDeviceIds = normalizeRequestedDeviceIds(request.deviceIds());
        if (requestedDeviceIds.isEmpty()) {
            throw new BusinessException("预约设备不能为空");
        }

        Map<String, Device> requestedDeviceMap = loadDeviceMap(requestedDeviceIds);
        throwIfMultiReservationBlocked("多设备预约参数校验失败", collectRequestShapeBlockingDevices(requestedDeviceIds, requestedDeviceMap));

        MultiReservationContext context = resolveMultiReservationContext(operatorId, operatorRole, request, requestedDeviceIds, requestedDeviceMap);
        List<String> orderedDeviceIds = requestedDeviceIds.stream().distinct().toList();
        throwIfMultiReservationBlocked("所选设备当前不可预约", collectDeviceSnapshotBlockingDevices(orderedDeviceIds, requestedDeviceMap));

        List<ReentrantLock> locks = acquireDeviceLocks(orderedDeviceIds);
        try {
            MultiReservationResponse response = transactionTemplate.execute(status -> {
                List<BlockingDeviceResponse> conflictBlockingDevices = collectConflictBlockingDevices(
                        orderedDeviceIds,
                        requestedDeviceMap,
                        request.startTime(),
                        request.endTime());
                throwIfMultiReservationBlocked("所选设备在当前时间段不可预约", conflictBlockingDevices);

                Reservation reservation = new Reservation();
                reservation.setId(UuidUtil.randomUuid());
                reservation.setUserId(context.targetUserId());
                reservation.setCreatedBy(operatorId);
                reservation.setReservationMode(context.reservationMode());
                reservation.setStartTime(request.startTime());
                reservation.setEndTime(request.endTime());
                reservation.setPurpose(request.purpose());
                reservation.setRemark(request.remark());
                reservation.setApprovalModeSnapshot(resolveAggregateApprovalMode(orderedDeviceIds, requestedDeviceMap));
                reservation.setStatus("PENDING_DEVICE_APPROVAL");
                reservation.setSignStatus("NOT_CHECKED_IN");
                reservationMapper.insert(reservation);
                reservationDeviceBackfillSupport.saveDeviceRelations(reservation.getId(), orderedDeviceIds);
                saveNotification(
                        context.targetUserId(),
                        "FIRST_APPROVAL_TODO",
                        "IN_APP",
                        "预约待审批",
                        "您的预约已提交，等待设备管理员审批",
                        reservation.getId(),
                        "RESERVATION");
                if ("ON_BEHALF".equals(context.reservationMode())) {
                    saveNotification(
                            context.targetUserId(),
                            "ON_BEHALF_CREATED",
                            "IN_APP",
                            "收到代预约",
                            "系统管理员已为您创建预约，请及时查看审批进度",
                            reservation.getId(),
                            "RESERVATION");
                }
                return new MultiReservationResponse(toResponse(reservation), orderedDeviceIds.size());
            });
            if (response == null) {
                throw new BusinessException("多设备预约创建失败");
            }
            return response;
        } finally {
            releaseDeviceLocks(locks);
        }
    }

    /**
     * 按指定预约模式创建预约。
     * <p>
     * 这里是预约主链路的统一落库入口：负责冲突检测、审批模式快照、初始状态、站内通知和最终动作回包组装。
     * 本人预约、代预约与批量预约都复用它，确保三条入口不会在审批口径上继续漂移。
     */
    @Override
    public ReservationResponse createReservationWithMode(
            String userId,
            String createdBy,
            String reservationMode,
            String batchId,
            CreateReservationRequest request) {
        ensureAllowedReservationApplicant(userId, reservationMode);
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
                reservation.setStartTime(request.startTime());
                reservation.setEndTime(request.endTime());
                reservation.setPurpose(request.purpose());
                reservation.setRemark(request.remark());
                reservation.setApprovalModeSnapshot(approvalMode);
                reservation.setStatus(reservationValidator.resolveInitialStatus(device, category.getDefaultApprovalMode()));
                reservation.setSignStatus("NOT_CHECKED_IN");
                reservationMapper.insert(reservation);
                reservationDeviceBackfillSupport.savePrimaryDeviceRelation(reservation.getId(), device.getId());
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

    /**
     * 创建代预约。
     * <p>
     * 只有 `SYSTEM_ADMIN` 才能代 `USER` 预约，因此这里先做角色和目标用户校验，
     * 再把模式显式固定为 `ON_BEHALF`，避免代预约链路偷偷复用本人预约口径。
     */
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

    /**
     * 执行预约签到。
     * <p>
     * 签到链路既要区分正常签到和超时签到，也要在超过 60 分钟后把预约推进为 `EXPIRED`。
     * 这里显式对 `BusinessException` 关闭事务回滚，是为了让“签到超时”这种业务拒绝在返回 400 的同时仍能把过期状态和通知稳定落库。
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ReservationResponse checkIn(String reservationId, String userId, String role, CheckInRequest request) {
        if (!"USER".equals(role)) {
            throw new BusinessException("只有普通用户可以执行签到");
        }
        Reservation reservation = mustFindReservation(reservationId);
        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException("只能签到本人预约");
        }
        if (!"APPROVED".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约状态不允许签到");
        }
        if (!"NOT_CHECKED_IN".equals(reservation.getSignStatus())) {
            throw new BusinessException("该预约已完成签到");
        }

        LocalDateTime checkInTime = request != null && request.checkInTime() != null
                ? request.checkInTime()
                : LocalDateTime.now();
        LocalDateTime normalStart = reservation.getStartTime().minusMinutes(30);
        LocalDateTime normalEnd = reservation.getStartTime().plusMinutes(30);
        LocalDateTime timeoutEnd = reservation.getStartTime().plusMinutes(60);
        if (checkInTime.isBefore(normalStart)) {
            throw new BusinessException("未到签到开放时间");
        }
        if (checkInTime.isAfter(timeoutEnd)) {
            reservation.setStatus("EXPIRED");
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            saveNotification(reservation.getUserId(), "CHECKIN_TIMEOUT_WARNING", "IN_APP", "签到超时", "签到时间已超过 60 分钟窗口，预约已过期", reservation.getId(), "RESERVATION");
            throw new BusinessException("签到超时，预约已过期");
        }

        reservation.setCheckedInAt(checkInTime);
        reservation.setSignStatus(checkInTime.isAfter(normalEnd) ? "CHECKED_IN_TIMEOUT" : "CHECKED_IN");
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return toResponse(reservation);
    }

    /**
     * 处理待人工预约。
     * <p>
     * 当预约已进入 `PENDING_MANUAL` 时，设备管理员要在这里给出最终裁决：通过则回到 `APPROVED`，拒绝则进入 `CANCELLED` 并补发取消通知。
     */
    @Override
    @Transactional
    public ReservationResponse manualProcess(String reservationId, String operatorId, String role, ManualProcessRequest request) {
        if (!"DEVICE_ADMIN".equals(role)) {
            throw new BusinessException("只有设备管理员可以处理待人工预约");
        }
        Reservation reservation = mustFindReservation(reservationId);
        if (!"PENDING_MANUAL".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约不处于待人工处理状态");
        }

        if (Boolean.TRUE.equals(request.approved())) {
            reservation.setStatus("APPROVED");
            reservation.setRemark(request.remark());
        } else {
            reservation.setStatus("CANCELLED");
            reservation.setCancelReason(request.remark());
            reservation.setCancelTime(LocalDateTime.now());
            saveNotification(reservation.getUserId(), "RESERVATION_CANCELLED", "IN_APP", "预约已取消", "待人工处理预约已被管理员取消", reservation.getId(), "RESERVATION");
        }
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return toResponse(reservation);
    }

    /**
     * 执行设备管理员第一审。
     * <p>
     * 第一审只负责把预约从 `PENDING_DEVICE_APPROVAL` 推进到 `PENDING_SYSTEM_APPROVAL` 或终态；
     * 这里不能让前端自行推导审批链结果，因此更新落库后要立刻返回统一 workflow context。
     */
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

    /**
     * 执行系统管理员第二审。
     * <p>
     * `DEVICE_THEN_SYSTEM` 模式下，第二审是真正把预约推进到最终审批结论的最后一步：
     * 通过时进入 `APPROVED`，拒绝时进入 `REJECTED`。这里既要守住“双审账号隔离”规则，
     * 也要把二审审批人、审批时间与最终状态一次性回传给前端，避免待审批页和详情页在动作完成后再次查库拼字段。
     */
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
        /*
         * 第二审是双审批链路的收口点：审批人、审批时间和最终状态必须在同一事务内落库，
         * 否则前端可能看到“状态已通过，但审批轨迹字段还是空值”的半完成快照。
         */
        reservation.setSystemApproverId(approverId);
        reservation.setSystemApprovedAt(LocalDateTime.now());
        reservation.setSystemApprovalRemark(request.remark());
        reservation.setStatus(Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED");
        reservation.setUpdatedAt(LocalDateTime.now());
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
        Reservation reservation = reservationMapper.findAggregateById(reservationId);
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

    /**
     * 校验预约申请人的角色边界。
     * <p>
     * 本人预约入口只允许 `USER` 使用；`DEVICE_ADMIN` 不得为自己创建预约，`SYSTEM_ADMIN` 也必须走代预约入口，
     * 这样才能把“本人预约”和“代预约”两条链路的角色边界稳定隔离，避免后台角色绕过正式代预约审计语义。
     */
    private void ensureAllowedReservationApplicant(String userId, String reservationMode) {
        if (!"SELF".equals(reservationMode)) {
            return;
        }
        User user = mustFindUser(userId);
        Role role = roleMapper.selectById(user.getRoleId());
        if (role == null || !"USER".equals(role.getName())) {
            throw new BusinessException("只有普通用户可以创建本人预约");
        }
    }

    /**
     * 统一整理多设备请求中的设备 ID。
     * <p>
     * 这里保留前端原始选择顺序，只做空白裁剪；真正的去重与上限校验仍交给后续阻塞原因收集逻辑，
     * 这样失败响应才能精确告诉调用方“哪台设备因为重复/超限而阻塞整单提交”。
     */
    private List<String> normalizeRequestedDeviceIds(List<String> deviceIds) {
        if (deviceIds == null) {
            return List.of();
        }
        return deviceIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(deviceId -> !deviceId.isEmpty())
                .toList();
    }

    /**
     * 收集请求形状层面的阻塞设备。
     * <p>
     * 重复设备与超上限都属于“请求本身已经不合法”，必须先于数据库冲突检测拦住，
     * 否则后端会在写事务里做无意义工作，还会让前端拿不到清晰的失败归因。
     */
    private List<BlockingDeviceResponse> collectRequestShapeBlockingDevices(
            List<String> requestedDeviceIds,
            Map<String, Device> requestedDeviceMap) {
        Map<String, BlockingDeviceResponse> blockingDevices = new LinkedHashMap<>();
        Set<String> seenDeviceIds = new HashSet<>();
        int uniqueDeviceCount = 0;
        for (String deviceId : requestedDeviceIds) {
            if (!seenDeviceIds.add(deviceId)) {
                blockingDevices.putIfAbsent(
                        deviceId,
                        buildBlockingDevice(
                                deviceId,
                                resolveDeviceName(deviceId, requestedDeviceMap),
                                "DEVICE_DUPLICATED",
                                "同一设备不能在单次多设备预约中重复选择"));
                continue;
            }
            uniqueDeviceCount++;
            if (uniqueDeviceCount > MAX_MULTI_RESERVATION_DEVICE_COUNT) {
                blockingDevices.putIfAbsent(
                        deviceId,
                        buildBlockingDevice(
                                deviceId,
                                resolveDeviceName(deviceId, requestedDeviceMap),
                                "DEVICE_LIMIT_EXCEEDED",
                                "单次预约最多只能选择 10 台设备"));
            }
        }
        return List.copyOf(blockingDevices.values());
    }

    /**
     * 收集设备快照层面的阻塞原因。
     * <p>
     * 该阶段只判断“设备现在是否值得继续进入事务”：
     * 设备不存在直接报 `DEVICE_NOT_FOUND`，设备状态不是 AVAILABLE 则报 `DEVICE_NOT_RESERVABLE`。
     */
    private List<BlockingDeviceResponse> collectDeviceSnapshotBlockingDevices(
            List<String> orderedDeviceIds,
            Map<String, Device> requestedDeviceMap) {
        List<BlockingDeviceResponse> blockingDevices = new ArrayList<>();
        for (String deviceId : orderedDeviceIds) {
            Device device = requestedDeviceMap.get(deviceId);
            if (device == null) {
                blockingDevices.add(buildBlockingDevice(deviceId, null, "DEVICE_NOT_FOUND", "设备不存在"));
                continue;
            }
            if (!"AVAILABLE".equals(device.getStatus())) {
                blockingDevices.add(buildBlockingDevice(deviceId, device.getName(), "DEVICE_NOT_RESERVABLE", "设备当前状态不可预约"));
            }
        }
        return blockingDevices;
    }

    /**
     * 收集时间冲突导致的阻塞设备。
     * <p>
     * 该校验必须放在按设备加锁之后、事务写入之前执行：
     * 只有这样才能让并发请求在同一把应用层锁保护下串行检查数据库事实，避免整单出现 partial success。
     */
    private List<BlockingDeviceResponse> collectConflictBlockingDevices(
            List<String> orderedDeviceIds,
            Map<String, Device> requestedDeviceMap,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        List<BlockingDeviceResponse> blockingDevices = new ArrayList<>();
        for (String deviceId : orderedDeviceIds) {
            List<Reservation> conflicts = reservationMapper.findConflictingReservations(deviceId, startTime, endTime);
            if (conflictDetector.hasConflict(conflicts, startTime, endTime)) {
                blockingDevices.add(buildBlockingDevice(
                        deviceId,
                        resolveDeviceName(deviceId, requestedDeviceMap),
                        "DEVICE_TIME_CONFLICT",
                        "设备在当前时间段已存在有效预约"));
            }
        }
        return blockingDevices;
    }

    /**
     * 解析多设备预约的角色上下文。
     * <p>
     * 多设备接口兼容“普通用户为自己预约”和“系统管理员代 USER 预约”两类场景；
     * 不允许设备管理员创建预约，也不允许普通用户借由 targetUserId 把自己伪装成代预约操作人。
     */
    private MultiReservationContext resolveMultiReservationContext(
            String operatorId,
            String operatorRole,
            CreateMultiReservationRequest request,
            List<String> requestedDeviceIds,
            Map<String, Device> requestedDeviceMap) {
        if ("DEVICE_ADMIN".equals(operatorRole)) {
            throwMultiReservationConflict(
                    "当前角色不能创建多设备预约",
                    buildPermissionDeniedBlockingDevices(requestedDeviceIds, requestedDeviceMap, "设备管理员不能创建多设备预约"));
        }
        if ("USER".equals(operatorRole)) {
            if (request.targetUserId() != null && !request.targetUserId().isBlank() && !operatorId.equals(request.targetUserId())) {
                throwMultiReservationConflict(
                        "当前角色不能创建多设备预约",
                        buildPermissionDeniedBlockingDevices(requestedDeviceIds, requestedDeviceMap, "普通用户只能为自己创建多设备预约"));
            }
            return new MultiReservationContext(operatorId, "SELF");
        }
        if (!"SYSTEM_ADMIN".equals(operatorRole)) {
            throwMultiReservationConflict(
                    "当前角色不能创建多设备预约",
                    buildPermissionDeniedBlockingDevices(requestedDeviceIds, requestedDeviceMap, "当前角色不能创建多设备预约"));
        }
        if (request.targetUserId() == null || request.targetUserId().isBlank()) {
            throwMultiReservationConflict(
                    "当前角色不能创建多设备预约",
                    buildPermissionDeniedBlockingDevices(requestedDeviceIds, requestedDeviceMap, "系统管理员发起多设备预约时必须指定目标用户"));
        }
        User targetUser = mustFindUser(request.targetUserId());
        Role targetRole = roleMapper.selectById(targetUser.getRoleId());
        if (targetRole == null || !"USER".equals(targetRole.getName())) {
            throw new BusinessException("系统管理员仅可代 USER 预约");
        }
        return new MultiReservationContext(targetUser.getId(), operatorId.equals(targetUser.getId()) ? "SELF" : "ON_BEHALF");
    }

    /**
     * 生成权限不足时的整单阻塞设备列表。
     * <p>
     * 权限问题虽然本质上是“调用人不能提交这张整单”，
     * 但前端失败面板仍以设备维度展示原因，因此这里把当前请求中的每台设备都标成 `DEVICE_PERMISSION_DENIED`。
     */
    private List<BlockingDeviceResponse> buildPermissionDeniedBlockingDevices(
            List<String> requestedDeviceIds,
            Map<String, Device> requestedDeviceMap,
            String reasonMessage) {
        Map<String, BlockingDeviceResponse> blockingDevices = new LinkedHashMap<>();
        for (String deviceId : requestedDeviceIds) {
            blockingDevices.putIfAbsent(
                    deviceId,
                    buildBlockingDevice(
                            deviceId,
                            resolveDeviceName(deviceId, requestedDeviceMap),
                            "DEVICE_PERMISSION_DENIED",
                            reasonMessage));
        }
        return List.copyOf(blockingDevices.values());
    }

    /**
     * 解析整单审批模式快照。
     * <p>
     * 多设备预约只有一条 reservation 主记录，因此审批模式快照必须在整单维度收敛成一个值；
     * 这里取“最严格优先”策略：只要任一设备要求 `DEVICE_THEN_SYSTEM`，整单就按双审批处理，避免把更严格的审批设备错误降级。
     */
    private String resolveAggregateApprovalMode(List<String> orderedDeviceIds, Map<String, Device> requestedDeviceMap) {
        for (String deviceId : orderedDeviceIds) {
            Device device = mustFindMappedValue(requestedDeviceMap, deviceId, "设备不存在");
            DeviceCategory category = mustFindCategory(device.getCategoryId());
            String approvalMode = reservationValidator.resolveApprovalMode(device, category.getDefaultApprovalMode());
            if ("DEVICE_THEN_SYSTEM".equals(approvalMode)) {
                return "DEVICE_THEN_SYSTEM";
            }
        }
        return "DEVICE_ONLY";
    }

    private List<ReentrantLock> acquireDeviceLocks(List<String> orderedDeviceIds) {
        List<String> sortedDeviceIds = new ArrayList<>(orderedDeviceIds);
        sortedDeviceIds.sort(Comparator.naturalOrder());
        List<ReentrantLock> locks = new ArrayList<>(sortedDeviceIds.size());
        for (String deviceId : sortedDeviceIds) {
            ReentrantLock lock = deviceLocks.computeIfAbsent(deviceId, key -> new ReentrantLock());
            lock.lock();
            locks.add(lock);
        }
        return locks;
    }

    private void releaseDeviceLocks(List<ReentrantLock> locks) {
        List<ReentrantLock> reversedLocks = new ArrayList<>(locks);
        Collections.reverse(reversedLocks);
        for (ReentrantLock lock : reversedLocks) {
            lock.unlock();
        }
    }

    private void throwIfMultiReservationBlocked(String message, List<BlockingDeviceResponse> blockingDevices) {
        if (!blockingDevices.isEmpty()) {
            throwMultiReservationConflict(message, blockingDevices);
        }
    }

    private void throwMultiReservationConflict(String message, List<BlockingDeviceResponse> blockingDevices) {
        throw new MultiReservationConflictException(message, new MultiReservationConflictResponse(List.copyOf(blockingDevices)));
    }

    private BlockingDeviceResponse buildBlockingDevice(String deviceId, String deviceName, String reasonCode, String reasonMessage) {
        return new BlockingDeviceResponse(deviceId, deviceName, reasonCode, reasonMessage);
    }

    private String resolveDeviceName(String deviceId, Map<String, Device> requestedDeviceMap) {
        Device device = requestedDeviceMap.get(deviceId);
        return device == null ? null : device.getName();
    }

    /**
     * 统一把动作型接口的返回值提升为“可直接继续渲染页面”的 workflow context。
     * <p>
     * 创建、一审、二审、签到和人工处理都可能改变审批人、签到时间、取消信息或设备关联字段；
     * 如果这里只返回轻量状态码，前端就不得不在每个页面自己保留旧快照或再次请求详情。
     * 因此这里统一回读最新预约详情，把动作结果收敛成与详情页同口径的动作响应。
     */
    private ReservationResponse toResponse(Reservation reservation) {
        /*
         * 必须基于最新落库事实重新组装返回值：
         * 这样既能拿到数据库已经写入的审批/签到时间，也能把设备名、预约人和审批人一并返回给前端页面继续渲染。
         */
        ReservationDetailResponse detailResponse = toDetailResponse(mustFindReservation(reservation.getId()));
        return new ReservationResponse(
                detailResponse.id(),
                detailResponse.batchId(),
                detailResponse.userId(),
                detailResponse.userName(),
                detailResponse.createdBy(),
                detailResponse.createdByName(),
                detailResponse.reservationMode(),
                detailResponse.deviceId(),
                detailResponse.deviceName(),
                detailResponse.deviceNumber(),
                detailResponse.deviceCount(),
                detailResponse.devices(),
                detailResponse.primaryDeviceId(),
                detailResponse.primaryDeviceName(),
                detailResponse.primaryDeviceNumber(),
                detailResponse.deviceStatus(),
                detailResponse.startTime(),
                detailResponse.endTime(),
                detailResponse.purpose(),
                detailResponse.remark(),
                detailResponse.status(),
                detailResponse.signStatus(),
                detailResponse.approvalModeSnapshot(),
                detailResponse.deviceApproverId(),
                detailResponse.deviceApproverName(),
                detailResponse.deviceApprovedAt(),
                detailResponse.deviceApprovalRemark(),
                detailResponse.systemApproverId(),
                detailResponse.systemApproverName(),
                detailResponse.systemApprovedAt(),
                detailResponse.systemApprovalRemark(),
                detailResponse.cancelReason(),
                detailResponse.cancelTime(),
                detailResponse.checkedInAt(),
                detailResponse.createdAt(),
                detailResponse.updatedAt());
    }

    /**
     * 组装预约列表项。
     * <p>
     * 列表页需要直接展示设备、人和取消信息，因此这里消费批量预加载结果进行组装，
     * 避免逐条 selectById 造成典型 N+1 查询。
     */
    private ReservationListItemResponse toListItemResponse(
            Reservation reservation,
            List<ReservationDevice> relations,
            Map<String, Device> deviceMap,
            Map<String, User> userMap) {
        List<ReservationDeviceSummaryResponse> devices = buildReservationDevices(reservation, relations, deviceMap);
        ReservationDeviceSummaryResponse primaryDevice = mustResolvePrimaryDevice(devices);
        User owner = mustFindMappedValue(userMap, reservation.getUserId(), "目标用户不存在");
        User creator = mustFindMappedValue(userMap, reservation.getCreatedBy(), "目标用户不存在");
        return new ReservationListItemResponse(
                reservation.getId(),
                reservation.getBatchId(),
                reservation.getUserId(),
                owner.getUsername(),
                reservation.getCreatedBy(),
                creator.getUsername(),
                reservation.getReservationMode(),
                primaryDevice.deviceId(),
                primaryDevice.deviceName(),
                primaryDevice.deviceNumber(),
                devices.size(),
                devices,
                primaryDevice.deviceId(),
                primaryDevice.deviceName(),
                primaryDevice.deviceNumber(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPurpose(),
                reservation.getStatus(),
                reservation.getSignStatus(),
                reservation.getApprovalModeSnapshot(),
                reservation.getCancelReason(),
                reservation.getCancelTime());
    }

    /**
     * 组装预约详情。
     * <p>
     * 详情页除了列表字段外，还要带上审批人、审批备注与设备当前状态，便于前端在单接口内完成联调。
     */
    private ReservationDetailResponse toDetailResponse(Reservation reservation) {
        Map<String, List<ReservationDevice>> relationMap = loadReservationDeviceRelationMap(List.of(reservation.getId()));
        Map<String, Device> deviceMap = loadDeviceMap(collectReadModelDeviceIds(List.of(reservation), relationMap));
        List<ReservationDeviceSummaryResponse> devices = buildReservationDevices(
                reservation,
                relationMap.getOrDefault(reservation.getId(), List.of()),
                deviceMap);
        ReservationDeviceSummaryResponse primaryDevice = mustResolvePrimaryDevice(devices);
        Device device = mustFindMappedValue(deviceMap, primaryDevice.deviceId(), "设备不存在");
        User owner = mustFindUser(reservation.getUserId());
        User creator = mustFindUser(reservation.getCreatedBy());
        User deviceApprover = findUser(reservation.getDeviceApproverId());
        User systemApprover = findUser(reservation.getSystemApproverId());
        return new ReservationDetailResponse(
                reservation.getId(),
                reservation.getBatchId(),
                reservation.getUserId(),
                owner.getUsername(),
                reservation.getCreatedBy(),
                creator.getUsername(),
                reservation.getReservationMode(),
                primaryDevice.deviceId(),
                primaryDevice.deviceName(),
                primaryDevice.deviceNumber(),
                devices.size(),
                devices,
                primaryDevice.deviceId(),
                primaryDevice.deviceName(),
                primaryDevice.deviceNumber(),
                device.getStatus(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPurpose(),
                reservation.getRemark(),
                reservation.getStatus(),
                reservation.getSignStatus(),
                reservation.getApprovalModeSnapshot(),
                reservation.getDeviceApproverId(),
                deviceApprover == null ? null : deviceApprover.getUsername(),
                reservation.getDeviceApprovedAt(),
                reservation.getDeviceApprovalRemark(),
                reservation.getSystemApproverId(),
                systemApprover == null ? null : systemApprover.getUsername(),
                reservation.getSystemApprovedAt(),
                reservation.getSystemApprovalRemark(),
                reservation.getCancelReason(),
                reservation.getCancelTime(),
                reservation.getCheckedInAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    /**
     * 统一校验当前预约是否对调用人可见。
     * <p>
     * USER 只能查看本人预约；管理角色保留管理视角。
     */
    private void ensureReservationVisible(Reservation reservation, String userId, String role) {
        if ("USER".equals(role) && !userId.equals(reservation.getUserId())) {
            throw new BusinessException("只能查看本人预约");
        }
    }

    /**
     * 预约列表、详情与取消只对白名单三角色开放。
     * <p>
     * 这里必须显式白名单，而不是简单使用“非 USER 即管理角色”的推断，
     * 否则任何伪造角色值都可能被错误放大为管理视角。
     */
    private void ensureSupportedReservationViewerRole(String role) {
        if (!"USER".equals(role) && !"DEVICE_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            throw new BusinessException("当前角色不允许访问预约信息");
        }
    }

    /**
     * 统一校验取消窗口。
     * <p>
     * 已取消、已拒绝、已过期等终态预约不能重复取消；开始后任何角色都不能取消；
     * 普通用户额外受“开始前超过 24 小时才能自助取消”的限制，24 小时内必须由管理角色处理。
     */
    private void ensureCancelable(Reservation reservation, String operatorId, String role, LocalDateTime referenceTime) {
        if ("CANCELLED".equals(reservation.getStatus())
                || "REJECTED".equals(reservation.getStatus())
                || "EXPIRED".equals(reservation.getStatus())) {
            throw new BusinessException("当前预约状态不允许取消");
        }
        /*
         * 一旦用户已签到，该预约就已经进入借用确认链路：后续应由设备管理员确认借用或转人工处理，
         * 不能再回到“直接取消”的分支，否则会破坏签到、借还与通知之间的状态真相。
         */
        if (reservation.getCheckedInAt() != null || !"NOT_CHECKED_IN".equals(reservation.getSignStatus())) {
            throw new BusinessException("已签到预约不可取消");
        }
        if (reservation.getStartTime().isBefore(referenceTime) || reservation.getStartTime().isEqual(referenceTime)) {
            throw new BusinessException("预约开始后不可取消");
        }
        if ("USER".equals(role)) {
            if (!operatorId.equals(reservation.getUserId())) {
                throw new BusinessException("只能取消本人预约");
            }
            if (!reservation.getStartTime().isAfter(referenceTime.plusHours(24))) {
                throw new BusinessException("开始前 24 小时内取消需管理员处理");
            }
            return;
        }
        if (!"DEVICE_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)) {
            throw new BusinessException("当前角色不允许取消预约");
        }
    }

    /**
     * 允许审批人字段为空时按空值返回，而不是把不存在的审批人误判成数据异常。
     */
    private User findUser(String userId) {
        return userId == null ? null : userMapper.selectById(userId);
    }

    private Map<String, List<ReservationDevice>> loadReservationDeviceRelationMap(Collection<String> reservationIds) {
        Map<String, List<ReservationDevice>> relationMap = new LinkedHashMap<>();
        List<String> uniqueIds = reservationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return relationMap;
        }
        for (ReservationDevice relation : reservationMapper.findDeviceRelationsByReservationIds(uniqueIds)) {
            relationMap.computeIfAbsent(relation.getReservationId(), key -> new ArrayList<>()).add(relation);
        }
        return relationMap;
    }

    private List<String> collectReadModelDeviceIds(
            Collection<Reservation> reservations,
            Map<String, List<ReservationDevice>> relationMap) {
        List<String> deviceIds = new ArrayList<>();
        for (Reservation reservation : reservations) {
            List<ReservationDevice> relations = relationMap.getOrDefault(reservation.getId(), List.of());
            if (!relations.isEmpty()) {
                relations.stream()
                        .map(ReservationDevice::getDeviceId)
                        .filter(Objects::nonNull)
                        .forEach(deviceIds::add);
                continue;
            }
            if (reservation.getDeviceId() != null) {
                deviceIds.add(reservation.getDeviceId());
            }
        }
        return deviceIds;
    }

    private List<ReservationDeviceSummaryResponse> buildReservationDevices(
            Reservation reservation,
            List<ReservationDevice> relations,
            Map<String, Device> deviceMap) {
        if (!relations.isEmpty()) {
            List<ReservationDeviceSummaryResponse> devices = new ArrayList<>();
            for (ReservationDevice relation : relations) {
                Device device = mustFindMappedValue(deviceMap, relation.getDeviceId(), "设备不存在");
                devices.add(toReservationDeviceSummary(device));
            }
            return List.copyOf(devices);
        }
        if (reservation.getDeviceId() == null) {
            return List.of();
        }
        Device fallbackDevice = mustFindMappedValue(deviceMap, reservation.getDeviceId(), "设备不存在");
        return List.of(toReservationDeviceSummary(fallbackDevice));
    }

    private ReservationDeviceSummaryResponse mustResolvePrimaryDevice(List<ReservationDeviceSummaryResponse> devices) {
        if (devices.isEmpty()) {
            throw new BusinessException("预约未关联设备");
        }
        return devices.getFirst();
    }

    private ReservationDeviceSummaryResponse toReservationDeviceSummary(Device device) {
        return new ReservationDeviceSummaryResponse(device.getId(), device.getName(), device.getDeviceNumber());
    }

    /**
     * 批量加载设备映射。
     * <p>
     * 列表页一次返回多条预约时，如果逐条查设备会形成 N+1 查询；这里统一按设备 ID 批量拉取后再组装。
     */
    private Map<String, Device> loadDeviceMap(Collection<String> deviceIds) {
        Map<String, Device> deviceMap = new HashMap<>();
        List<String> uniqueIds = deviceIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return deviceMap;
        }
        deviceMapper.selectByIds(uniqueIds).forEach(device -> deviceMap.put(device.getId(), device));
        return deviceMap;
    }

    /**
     * 批量加载用户映射。
     * <p>
     * 预约列表同时依赖预约人和创建人两个用户视角，这里一次性批量查询后放入 Map，
     * 避免记录数增长时产生倍增查询。
     */
    private Map<String, User> loadUserMap(Collection<String> userIds) {
        Map<String, User> userMap = new HashMap<>();
        List<String> uniqueIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return userMap;
        }
        userMapper.selectByIds(uniqueIds).forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    /**
     * 从批量映射结果中读取必需对象。
     * <p>
     * 如果批量查询后仍缺字段，说明数据已损坏，必须显式抛出业务异常而不是静默返回空值。
     */
    private <T> T mustFindMappedValue(Map<String, T> valueMap, String id, String message) {
        T value = valueMap.get(id);
        if (value == null) {
            throw new BusinessException(message);
        }
        return value;
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

    private record MultiReservationContext(String targetUserId, String reservationMode) {
    }
}
