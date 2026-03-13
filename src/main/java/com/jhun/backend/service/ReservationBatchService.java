package com.jhun.backend.service;

import com.jhun.backend.dto.reservationbatch.CreateReservationBatchRequest;
import com.jhun.backend.dto.reservationbatch.ReservationBatchResponse;

/**
 * 预约批次服务。
 */
public interface ReservationBatchService {

    ReservationBatchResponse createBatch(String operatorId, String operatorRole, CreateReservationBatchRequest request);

    ReservationBatchResponse getBatch(String batchId, String operatorId, String operatorRole);
}
