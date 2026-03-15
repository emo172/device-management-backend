package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.reservation.AuditReservationRequest;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.dto.reservation.CheckInRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ManualProcessRequest;
import com.jhun.backend.dto.reservation.ProxyReservationRequest;
import com.jhun.backend.dto.reservation.ReservationDetailResponse;
import com.jhun.backend.dto.reservation.ReservationPageResponse;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约控制器。
 * <p>
 * 当前阶段承载预约创建、列表、详情、取消、一审、二审、签到和人工处理等接口，
 * 既要服务普通用户查看和取消本人预约，也要服务管理角色按管理视角处理预约流转。
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 预约列表接口。
     * <p>
     * 控制层把当前登录人的 userId 与 role 一并下传给服务层，由服务层统一执行“USER 仅看本人、管理角色看管理视角”的口径，
     * 避免前端仅靠查询参数切换就绕过权限边界。
     */
    @GetMapping
    public Result<ReservationPageResponse> list(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.success(reservationService.listReservations(principal.userId(), principal.role(), page, size));
    }

    /**
     * 预约详情接口。
     * <p>
     * 详情接口沿用与列表相同的权限口径，防止普通用户通过已知预约 ID 直接查看他人预约明细。
     */
    @GetMapping("/{id}")
    public Result<ReservationDetailResponse> detail(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(reservationService.getReservationDetail(reservationId, principal.userId(), principal.role()));
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

    @PostMapping("/{id}/check-in")
    public Result<ReservationResponse> checkIn(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody CheckInRequest request) {
        return Result.success(reservationService.checkIn(reservationId, principal.userId(), principal.role(), request));
    }

    /**
     * 取消预约接口。
     * <p>
     * 普通用户的自助取消受“开始前超过 24 小时”窗口限制；24 小时内但尚未开始的预约需要管理角色处理；开始后则任何角色都不能直接取消。
     */
    @PostMapping("/{id}/cancel")
    public Result<ReservationDetailResponse> cancel(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) CancelReservationRequest request) {
        return Result.success(reservationService.cancelReservation(reservationId, principal.userId(), principal.role(), request));
    }

    @PutMapping("/{id}/manual-process")
    public Result<ReservationResponse> manualProcess(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ManualProcessRequest request) {
        return Result.success(reservationService.manualProcess(reservationId, principal.userId(), principal.role(), request));
    }
}
