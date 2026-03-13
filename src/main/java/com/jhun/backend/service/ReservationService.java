package com.jhun.backend.service;

import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;

/**
 * 预约服务。
 */
public interface ReservationService {

    ReservationResponse createReservation(String userId, String createdBy, CreateReservationRequest request);

    ReservationResponse deviceApprove(String reservationId, String approverId, String role, AuditReservationRequest request);

    ReservationResponse systemApprove(String reservationId, String approverId, String role, AuditReservationRequest request);
}
