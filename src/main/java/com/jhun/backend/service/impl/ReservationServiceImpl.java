package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ManualProcessRequest;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationListItemResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        Map<String, Device> deviceMap = loadDeviceMap(reservations.stream().map(Reservation::getDeviceId).toList());
        Map<String, User> userMap = loadUserMap(reservations.stream()
                .flatMap(reservation -> java.util.stream.Stream.of(reservation.getUserId(), reservation.getCreatedBy()))
                .filter(Objects::nonNull)
                .toList());
        List<ReservationListItemResponse> records = reservations.stream()
                .map(reservation -> toListItemResponse(reservation, deviceMap, userMap))
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
            Map<String, Device> deviceMap,
            Map<String, User> userMap) {
        Device device = mustFindMappedValue(deviceMap, reservation.getDeviceId(), "设备不存在");
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
                reservation.getDeviceId(),
                device.getName(),
                device.getDeviceNumber(),
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
        Device device = mustFindDevice(reservation.getDeviceId());
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
                reservation.getDeviceId(),
                device.getName(),
                device.getDeviceNumber(),
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
}
