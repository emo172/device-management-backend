package com.jhun.backend.service;

import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.CreateMultiReservationRequest;
import com.jhun.backend.dto.reservation.ManualProcessRequest;
import com.jhun.backend.dto.reservation.MultiReservationResponse;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;

/**
 * 预约服务。
 */
public interface ReservationService {

    /**
     * 查询预约列表。
     * <p>
     * 普通用户只能查询本人预约，管理角色则查询管理视角列表；分页参数在服务层统一兜底，避免控制层散落重复逻辑。
     */
    ReservationPageResponse listReservations(String userId, String role, int page, int size);

    /**
     * 查询预约详情。
     * <p>
     * 详情权限口径必须与列表保持一致，防止通过已知 ID 越权查看他人预约。
     */
    ReservationDetailResponse getReservationDetail(String reservationId, String userId, String role);

    /**
     * 取消预约。
     * <p>
     * 普通用户只允许在开始前超过 24 小时时取消本人预约；管理角色可处理 24 小时内但尚未开始的预约。
     */
    ReservationDetailResponse cancelReservation(String reservationId, String operatorId, String role, CancelReservationRequest request);

    ReservationResponse createReservation(String userId, String createdBy, CreateReservationRequest request);

    /**
     * 创建多设备单预约。
     * <p>
     * 新接口要求在单事务中完成整单校验与写入，任一设备失败都必须回滚整单，
     * 并把阻塞设备清单通过 409 失败响应返回给调用方。
     */
    MultiReservationResponse createMultiReservation(String operatorId, String operatorRole, CreateMultiReservationRequest request);

    ReservationResponse createReservationWithMode(
            String userId,
            String createdBy,
            String reservationMode,
            String batchId,
            CreateReservationRequest request);

    ReservationResponse createProxyReservation(String operatorId, String operatorRole, ProxyReservationRequest request);

    ReservationResponse checkIn(String reservationId, String userId, String role, CheckInRequest request);

    ReservationResponse manualProcess(String reservationId, String operatorId, String role, ManualProcessRequest request);

    ReservationResponse deviceApprove(String reservationId, String approverId, String role, AuditReservationRequest request);

    ReservationResponse systemApprove(String reservationId, String approverId, String role, AuditReservationRequest request);
}
