package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约控制器。
 * <p>
 * 当前阶段提供预约创建、第一审和第二审接口，为预约主链路上半段联调提供入口。
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public Result<ReservationResponse> create(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody CreateReservationRequest request) {
        return Result.success(reservationService.createReservation(principal.userId(), principal.userId(), request));
    }

    @PostMapping("/proxy")
    public Result<ReservationResponse> createProxy(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ProxyReservationRequest request) {
        return Result.success(reservationService.createProxyReservation(principal.userId(), principal.role(), request));
    }

    @PostMapping("/{id}/audit")
    public Result<ReservationResponse> deviceAudit(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody AuditReservationRequest request) {
        return Result.success(reservationService.deviceApprove(reservationId, principal.userId(), principal.role(), request));
    }

    @PostMapping("/{id}/system-audit")
    public Result<ReservationResponse> systemAudit(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody AuditReservationRequest request) {
        return Result.success(reservationService.systemApprove(reservationId, principal.userId(), principal.role(), request));
    }
}
