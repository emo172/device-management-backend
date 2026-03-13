package com.jhun.backend.service;

import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ManualProcessRequest;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;

/**
 * 预约服务。
 */
public interface ReservationService {

    ReservationResponse createReservation(String userId, String createdBy, CreateReservationRequest request);

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
