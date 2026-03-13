package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.reservationbatch.CreateReservationBatchRequest;
import com.jhun.backend.dto.reservationbatch.ReservationBatchResponse;
import com.jhun.backend.service.ReservationBatchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约批次控制器。
 * <p>
 * 提供批量预约创建与批次汇总查询接口，供 USER 和 SYSTEM_ADMIN 按角色边界发起批量预约。
 */
@RestController
@RequestMapping("/api/reservation-batches")
public class ReservationBatchController {

    private final ReservationBatchService reservationBatchService;

    public ReservationBatchController(ReservationBatchService reservationBatchService) {
        this.reservationBatchService = reservationBatchService;
    }

    @PostMapping
    public Result<ReservationBatchResponse> createBatch(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody CreateReservationBatchRequest request) {
        return Result.success(reservationBatchService.createBatch(principal.userId(), principal.role(), request));
    }

    @GetMapping("/{id}")
    public Result<ReservationBatchResponse> getBatch(
            @PathVariable("id") String batchId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(reservationBatchService.getBatch(batchId, principal.userId(), principal.role()));
    }
}
