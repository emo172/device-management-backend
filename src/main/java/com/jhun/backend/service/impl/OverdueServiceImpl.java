package com.jhun.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.overdue.OverdueRecordPageResponse;
import com.jhun.backend.dto.overdue.OverdueRecordResponse;
import com.jhun.backend.dto.overdue.ProcessOverdueRequest;
import com.jhun.backend.entity.BorrowRecord;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.OverdueRecord;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.BorrowRecordMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.OverdueRecordMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.OverdueService;
import com.jhun.backend.util.UuidUtil;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 逾期服务实现。
 * <p>
 * 当前阶段聚焦 Task 12 的最小闭环：
 * 一是基于 borrow_record.expected_return_time 识别新逾期并刷新已逾期时长；
 * 二是按 4 小时 / 96 小时阈值驱动 RESTRICTED 与 FROZEN；
 * 三是提供设备管理员处理入口、C-06 逾期提醒与 C-07 限制自动释放。
 */
@Service
public class OverdueServiceImpl implements OverdueService {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final String AUTO_RESTRICT_REASON = "AUTO_OVERDUE_RESTRICTED:借还记录逾期达到 4 小时及以上";
    private static final String AUTO_FROZEN_REASON = "AUTO_OVERDUE_FROZEN:借还记录逾期达到 96 小时及以上";
    private static final Set<String> ALLOWED_PROCESSING_METHODS = Set.of("WARNING", "COMPENSATION", "CONTINUE");

    private final OverdueRecordMapper overdueRecordMapper;
    private final BorrowRecordMapper borrowRecordMapper;
    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public OverdueServiceImpl(
            OverdueRecordMapper overdueRecordMapper,
            BorrowRecordMapper borrowRecordMapper,
            DeviceMapper deviceMapper,
            UserMapper userMapper,
            NotificationRecordMapper notificationRecordMapper) {
        this.overdueRecordMapper = overdueRecordMapper;
        this.borrowRecordMapper = borrowRecordMapper;
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    /**
     * 查询逾期记录分页列表。
     * <p>
     * 普通用户只能看到本人逾期记录，管理角色保留全量视角；
     * 这样可以同时满足用户自助查询和管理员治理排查两个前端场景。
     */
    @Override
    public OverdueRecordPageResponse listOverdueRecords(String userId, String role, int page, int size, String processingStatus) {
        String visibleUserId = "USER".equals(role) ? userId : null;
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = overdueRecordMapper.countByConditions(processingStatus, visibleUserId);
        List<OverdueRecordResponse> records = overdueRecordMapper.findPageByConditions(processingStatus, visibleUserId, safeSize, offset)
                .stream()
                .map(this::toResponse)
                .toList();
        return new OverdueRecordPageResponse(total, records);
    }

    /**
     * 查询单条逾期记录详情。
     * <p>
     * 详情接口沿用与列表一致的可见性规则，防止普通用户通过已知 ID 直接查看他人逾期信息。
     */
    @Override
    public OverdueRecordResponse getOverdueRecordDetail(String overdueRecordId, String userId, String role) {
        OverdueRecord overdueRecord = mustFindOverdueRecord(overdueRecordId);
        ensureVisible(overdueRecord, userId, role);
        return toResponse(overdueRecord);
    }

    /**
     * 处理逾期记录。
     * <p>
     * 仅 DEVICE_ADMIN 可以写入处理结果，且处理方式必须命中正式枚举，
     * 防止 SYSTEM_ADMIN 越权处理或脏值破坏逾期审计字段口径。
     */
    @Override
    @Transactional
    public OverdueRecordResponse processOverdueRecord(String overdueRecordId, String operatorId, String role, ProcessOverdueRequest request) {
        ensureDeviceAdmin(role, "只有设备管理员可以处理逾期记录");
        if (request == null) {
            throw new BusinessException("逾期处理请求不能为空");
        }
        if (request.processingMethod() == null || request.processingMethod().isBlank()) {
            throw new BusinessException("处理方式不能为空");
        }
        if (!ALLOWED_PROCESSING_METHODS.contains(request.processingMethod())) {
            throw new BusinessException("处理方式仅支持 WARNING、COMPENSATION、CONTINUE");
        }
        BigDecimal compensationAmount = request.compensationAmount() == null ? ZERO_AMOUNT : request.compensationAmount();
        if (compensationAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("赔偿金额不能为负数");
        }

        OverdueRecord overdueRecord = mustFindOverdueRecord(overdueRecordId);
        int updatedRows = overdueRecordMapper.updateProcessingResult(
                overdueRecordId,
                request.processingMethod(),
                request.remark(),
                operatorId,
                LocalDateTime.now(),
                compensationAmount,
                LocalDateTime.now());
        if (updatedRows != 1) {
            throw new BusinessException("当前逾期记录已处理，不能重复处理");
        }

        overdueRecord.setProcessingStatus("PROCESSED");
        overdueRecord.setProcessingMethod(request.processingMethod());
        overdueRecord.setProcessingRemark(request.remark());
        overdueRecord.setProcessorId(operatorId);
        overdueRecord.setProcessingTime(LocalDateTime.now());
        overdueRecord.setCompensationAmount(compensationAmount);
        overdueRecord.setUpdatedAt(LocalDateTime.now());
        return toResponse(overdueRecord);
    }

    /**
     * 执行逾期识别。
     * <p>
     * 该方法同时负责首次生成 overdue_record 和刷新已逾期记录时长，
     * 确保 4 小时与 96 小时两个冻结阈值都能随着时间推进被重新评估。
     */
    @Override
    @Transactional
    public void detectOverdues(LocalDateTime referenceTime) {
        LocalDateTime effectiveTime = referenceTime == null ? LocalDateTime.now() : referenceTime;

        /*
         * 先刷新已经处于 OVERDUE 的借还记录，确保跨越 96 小时阈值时能够从 RESTRICTED 升级到 FROZEN；
         * 否则只在“首次进入逾期”那一刻判级，会把后续继续恶化的逾期长期卡在旧等级。
         */
        for (BorrowRecord borrowRecord : borrowRecordMapper.findActiveOverdueRecords()) {
            refreshExistingOverdueRecord(borrowRecord, effectiveTime);
        }

        for (BorrowRecord borrowRecord : borrowRecordMapper.findBorrowedExpectedReturnBefore(effectiveTime)) {
            createNewOverdueRecord(borrowRecord, effectiveTime);
        }
    }

    /**
     * 发送尚未落库的正式逾期提醒。
     * <p>
     * 这里先通过 CAS 更新占有发送资格，再写入通知记录，避免并发任务把同一条逾期记录重复发送多次。
     */
    @Override
    @Transactional
    public void sendPendingOverdueNotifications(LocalDateTime executeTime) {
        LocalDateTime sentAt = executeTime == null ? LocalDateTime.now() : executeTime;
        for (OverdueRecord overdueRecord : overdueRecordMapper.findNotificationPendingRecords()) {
            if (overdueRecordMapper.updateNotificationSentIfPending(overdueRecord.getId(), 1, sentAt) != 1) {
                continue;
            }
            saveNotification(
                    overdueRecord.getUserId(),
                    "OVERDUE_WARNING",
                    "逾期提醒",
                    "您的借还记录已逾期，请尽快归还设备并联系设备管理员处理。",
                    overdueRecord.getId(),
                    "OVERDUE",
                    sentAt);
        }
    }

    /**
     * 自动解除逾期来源的预约限制。
     * <p>
     * 仅当用户当前为 RESTRICTED、限制原因为逾期自动限制、且已不存在 OVERDUE 借还记录时才解除，
     * 防止误释放人工限制或仍在逾期中的账户。
     */
    @Override
    @Transactional
    public void releaseRestrictedUsers(LocalDateTime executeTime) {
        LocalDateTime effectiveTime = executeTime == null ? LocalDateTime.now() : executeTime;
        List<User> restrictedUsers = userMapper.selectList(new QueryWrapper<User>().eq("freeze_status", "RESTRICTED"));
        for (User user : restrictedUsers) {
            /*
             * C-07 只能自动解除“因逾期自动进入 RESTRICTED”的账户。
             * 人工限制用户同样可能处于 RESTRICTED，但 freeze_reason 不会带自动逾期标记，必须跳过。
             */
            if (!AUTO_RESTRICT_REASON.equals(user.getFreezeReason())) {
                continue;
            }
            if (borrowRecordMapper.countActiveOverdueByUserId(user.getId()) > 0) {
                continue;
            }
            user.setFreezeStatus("NORMAL");
            user.setFreezeReason("逾期限制已自动解除");
            user.setFreezeExpireTime(null);
            user.setUpdatedAt(effectiveTime);
            userMapper.updateById(user);
            saveNotification(
                    user.getId(),
                    "ACCOUNT_FREEZE_UNFREEZE",
                    "账户限制已解除",
                    "系统检测到您已不存在逾期中的借还记录，预约限制已自动解除。",
                    user.getId(),
                    "USER",
                    effectiveTime);
        }
    }

    /**
     * 处理首次从 BORROWED 进入 OVERDUE 的借还记录。
     * <p>
     * 这里先用 CAS 把 borrow_record.status 从 BORROWED 改为 OVERDUE，再插入 overdue_record，
     * 避免并发调度或重复执行时生成重复逾期条目。
     */
    private void createNewOverdueRecord(BorrowRecord borrowRecord, LocalDateTime referenceTime) {
        int overdueHours = calculateOverdueHours(borrowRecord.getExpectedReturnTime(), referenceTime);
        if (overdueHours <= 0) {
            return;
        }
        int updatedRows = borrowRecordMapper.markOverdueIfBorrowed(borrowRecord.getId(), referenceTime);
        if (updatedRows != 1) {
            return;
        }
        OverdueRecord existing = overdueRecordMapper.findByBorrowRecordId(borrowRecord.getId());
        if (existing == null) {
            OverdueRecord overdueRecord = new OverdueRecord();
            overdueRecord.setId(UuidUtil.randomUuid());
            overdueRecord.setBorrowRecordId(borrowRecord.getId());
            overdueRecord.setUserId(borrowRecord.getUserId());
            overdueRecord.setDeviceId(borrowRecord.getDeviceId());
            overdueRecord.setOverdueHours(overdueHours);
            overdueRecord.setOverdueDays(calculateOverdueDays(overdueHours));
            overdueRecord.setProcessingStatus("PENDING");
            overdueRecord.setCompensationAmount(ZERO_AMOUNT);
            overdueRecord.setNotificationSent(0);
            overdueRecord.setCreatedAt(referenceTime);
            overdueRecord.setUpdatedAt(referenceTime);
            overdueRecordMapper.insert(overdueRecord);
            applyFreezeStrategy(borrowRecord.getUserId(), overdueHours, referenceTime);
            return;
        }
        refreshExistingOverdueRecord(borrowRecord, referenceTime);
    }

    /**
     * 刷新已存在逾期记录的时长快照，并根据最新时长重新评估是否需要升级冻结等级。
     */
    private void refreshExistingOverdueRecord(BorrowRecord borrowRecord, LocalDateTime referenceTime) {
        int overdueHours = calculateOverdueHours(borrowRecord.getExpectedReturnTime(), referenceTime);
        if (overdueHours <= 0) {
            return;
        }
        OverdueRecord overdueRecord = overdueRecordMapper.findByBorrowRecordId(borrowRecord.getId());
        if (overdueRecord == null) {
            createNewOverdueRecord(borrowRecord, referenceTime);
            return;
        }
        overdueRecordMapper.updateDuration(
                overdueRecord.getId(),
                overdueHours,
                calculateOverdueDays(overdueHours),
                referenceTime);
        applyFreezeStrategy(borrowRecord.getUserId(), overdueHours, referenceTime);
    }

    /**
     * 按逾期时长应用冻结策略。
     * <p>
     * 规则口径严格遵循真相源：
     * 1. 小于 4 小时只生成逾期事实，不改用户冻结状态；
     * 2. 4 小时及以上进入 RESTRICTED；
     * 3. 96 小时及以上升级到 FROZEN，且自动释放任务不会处理 FROZEN。
     */
    private void applyFreezeStrategy(String userId, int overdueHours, LocalDateTime effectiveTime) {
        User user = mustFindUser(userId);
        if (overdueHours >= 96) {
            if (!"FROZEN".equals(user.getFreezeStatus())) {
                user.setFreezeStatus("FROZEN");
                user.setFreezeReason(AUTO_FROZEN_REASON);
                user.setFreezeExpireTime(null);
                user.setUpdatedAt(effectiveTime);
                userMapper.updateById(user);
                saveNotification(
                        userId,
                        "ACCOUNT_FREEZE_UNFREEZE",
                        "账户已冻结",
                        "您的设备借还记录逾期已达到 96 小时及以上，账户已被冻结，请联系系统管理员处理。",
                        userId,
                        "USER",
                        effectiveTime);
            }
            return;
        }
        if (overdueHours >= 4 && "NORMAL".equals(user.getFreezeStatus())) {
            user.setFreezeStatus("RESTRICTED");
            user.setFreezeReason(AUTO_RESTRICT_REASON);
            user.setFreezeExpireTime(null);
            user.setUpdatedAt(effectiveTime);
            userMapper.updateById(user);
            saveNotification(
                    userId,
                    "ACCOUNT_FREEZE_UNFREEZE",
                    "账户已受限",
                    "您的设备借还记录逾期已达到 4 小时及以上，账户已进入预约限制状态，请尽快归还设备。",
                    userId,
                    "USER",
                    effectiveTime);
        }
    }

    private int calculateOverdueHours(LocalDateTime expectedReturnTime, LocalDateTime referenceTime) {
        if (expectedReturnTime == null || referenceTime == null || !referenceTime.isAfter(expectedReturnTime)) {
            return 0;
        }
        return Math.toIntExact(Duration.between(expectedReturnTime, referenceTime).toHours());
    }

    private int calculateOverdueDays(int overdueHours) {
        return overdueHours <= 0 ? 0 : overdueHours / 24;
    }

    private OverdueRecord mustFindOverdueRecord(String overdueRecordId) {
        OverdueRecord overdueRecord = overdueRecordMapper.selectById(overdueRecordId);
        if (overdueRecord == null) {
            throw new BusinessException("逾期记录不存在");
        }
        return overdueRecord;
    }

    private User mustFindUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void ensureVisible(OverdueRecord overdueRecord, String userId, String role) {
        if ("USER".equals(role) && !userId.equals(overdueRecord.getUserId())) {
            throw new BusinessException("只能查看本人逾期记录");
        }
    }

    private void ensureDeviceAdmin(String role, String message) {
        if (!"DEVICE_ADMIN".equals(role)) {
            throw new BusinessException(message);
        }
    }

    private void saveNotification(
            String userId,
            String type,
            String title,
            String content,
            String relatedId,
            String relatedType,
            LocalDateTime sentAt) {
        NotificationRecord record = new NotificationRecord();
        record.setId(UuidUtil.randomUuid());
        record.setUserId(userId);
        record.setNotificationType(type);
        record.setChannel("IN_APP");
        record.setTitle(title);
        record.setContent(content);
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setSentAt(sentAt);
        record.setReadFlag(0);
        record.setRelatedId(relatedId);
        record.setRelatedType(relatedType);
        record.setCreatedAt(sentAt);
        record.setUpdatedAt(sentAt);
        notificationRecordMapper.insert(record);
    }

    private OverdueRecordResponse toResponse(OverdueRecord overdueRecord) {
        User user = userMapper.selectById(overdueRecord.getUserId());
        Device device = deviceMapper.selectById(overdueRecord.getDeviceId());
        return new OverdueRecordResponse(
                overdueRecord.getId(),
                overdueRecord.getBorrowRecordId(),
                overdueRecord.getUserId(),
                resolveUserName(user),
                overdueRecord.getDeviceId(),
                device == null ? null : device.getName(),
                device == null ? null : device.getDeviceNumber(),
                overdueRecord.getOverdueHours(),
                overdueRecord.getOverdueDays(),
                overdueRecord.getProcessingStatus(),
                overdueRecord.getProcessingMethod(),
                overdueRecord.getProcessingRemark(),
                overdueRecord.getProcessorId(),
                overdueRecord.getProcessingTime(),
                overdueRecord.getCompensationAmount(),
                overdueRecord.getNotificationSent(),
                overdueRecord.getCreatedAt());
    }

    /**
     * 逾期页与借还页共用“实名优先、用户名兜底”的展示口径，避免同一用户在不同页面显示出两套名字来源。
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
}
