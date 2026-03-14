package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservationbatch.CreateReservationBatchRequest;
import com.jhun.backend.dto.reservationbatch.ReservationBatchResponse;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.ReservationBatch;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.ReservationBatchMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.ReservationBatchService;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.util.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约批次服务实现。
 * <p>
 * 负责批次创建、批次汇总回查，并落地批次结果通知，确保批量预约使用正式表 reservation_batch。
 */
@Service
public class ReservationBatchServiceImpl implements ReservationBatchService {

    private final ReservationBatchMapper reservationBatchMapper;
    private final ReservationService reservationService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public ReservationBatchServiceImpl(
            ReservationBatchMapper reservationBatchMapper,
            ReservationService reservationService,
            UserMapper userMapper,
            RoleMapper roleMapper,
            NotificationRecordMapper notificationRecordMapper) {
        this.reservationBatchMapper = reservationBatchMapper;
        this.reservationService = reservationService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Override
    @Transactional
    public ReservationBatchResponse createBatch(String operatorId, String operatorRole, CreateReservationBatchRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("批量预约条目不能为空");
        }

        BatchContext context = resolveBatchContext(operatorId, operatorRole, request.targetUserId());
        ReservationBatch batch = new ReservationBatch();
        batch.setId(UuidUtil.randomUuid());
        batch.setBatchNo("RB-" + System.currentTimeMillis());
        batch.setCreatedBy(operatorId);
        batch.setReservationCount(request.items().size());
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setStatus("PROCESSING");
        reservationBatchMapper.insert(batch);

        int successCount = 0;
        int failedCount = 0;
        for (CreateReservationBatchRequest.BatchReservationItem item : request.items()) {
            try {
                reservationService.createReservationWithMode(
                        context.targetUserId(),
                        operatorId,
                        context.reservationMode(),
                        batch.getId(),
                        new CreateReservationRequest(item.deviceId(), item.startTime(), item.endTime(), item.purpose(), item.remark()));
                successCount++;
            } catch (BusinessException ex) {
                failedCount++;
            }
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(resolveBatchStatus(successCount, failedCount));
        reservationBatchMapper.updateById(batch);

        saveNotification(
                operatorId,
                "BATCH_RESERVATION_RESULT",
                "IN_APP",
                "批量预约执行结果",
                "批量预约执行完成：成功 %d 条，失败 %d 条".formatted(successCount, failedCount),
                batch.getId(),
                "RESERVATION_BATCH");
        if (!operatorId.equals(context.targetUserId())) {
            saveNotification(
                    context.targetUserId(),
                    "BATCH_RESERVATION_RESULT",
                    "IN_APP",
                    "批量预约执行结果",
                    "管理员为您发起的批量预约执行完成：成功 %d 条，失败 %d 条".formatted(successCount, failedCount),
                    batch.getId(),
                    "RESERVATION_BATCH");
        }

        return toResponse(batch);
    }

    @Override
    public ReservationBatchResponse getBatch(String batchId, String operatorId, String operatorRole) {
        ReservationBatch batch = mustFindBatch(batchId);
        if (!"SYSTEM_ADMIN".equals(operatorRole) && !operatorId.equals(batch.getCreatedBy())) {
            throw new BusinessException("仅批次创建人或系统管理员可以查看批次详情");
        }
        return toResponse(batch);
    }

    private ReservationBatch mustFindBatch(String batchId) {
        ReservationBatch batch = reservationBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("预约批次不存在");
        }
        return batch;
    }

    private BatchContext resolveBatchContext(String operatorId, String operatorRole, String targetUserId) {
        if ("DEVICE_ADMIN".equals(operatorRole)) {
            throw new BusinessException("设备管理员不能创建预约或批量预约");
        }
        if ("USER".equals(operatorRole)) {
            if (targetUserId != null && !targetUserId.isBlank() && !operatorId.equals(targetUserId)) {
                throw new BusinessException("普通用户只能为自己创建批量预约");
            }
            return new BatchContext(operatorId, "SELF");
        }
        if (!"SYSTEM_ADMIN".equals(operatorRole)) {
            throw new BusinessException("当前角色不能创建批量预约");
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new BusinessException("系统管理员发起批量预约时必须指定目标用户");
        }

        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new BusinessException("目标用户不存在");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        if (role == null || !"USER".equals(role.getName())) {
            throw new BusinessException("系统管理员仅可为 USER 发起批量预约");
        }
        return new BatchContext(targetUserId, operatorId.equals(targetUserId) ? "SELF" : "ON_BEHALF");
    }

    private String resolveBatchStatus(int successCount, int failedCount) {
        if (failedCount == 0) {
            return "SUCCESS";
        }
        if (successCount == 0) {
            return "FAILED";
        }
        return "PARTIAL_SUCCESS";
    }

    private ReservationBatchResponse toResponse(ReservationBatch batch) {
        return new ReservationBatchResponse(
                batch.getId(),
                batch.getBatchNo(),
                batch.getCreatedBy(),
                batch.getReservationCount(),
                batch.getSuccessCount(),
                batch.getFailedCount(),
                batch.getStatus());
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

    private record BatchContext(String targetUserId, String reservationMode) {
    }
}
