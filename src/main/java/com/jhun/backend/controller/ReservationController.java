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

    /**
     * 创建本人预约。
     * <p>
     * 控制层显式把“创建人”和“预约申请人”都绑定为当前登录用户，
     * 防止普通用户通过自行构造请求体把本人预约入口伪造成代预约入口。
     */
    @PostMapping
    public Result<ReservationResponse> create(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody CreateReservationRequest request) {
        return Result.success(reservationService.createReservation(principal.userId(), principal.userId(), request));
    }

    /**
     * 创建代预约。
     * <p>
     * 代预约只允许服务层按当前角色继续校验目标用户与审批口径，
     * 控制层这里保持最小转发，确保 `SYSTEM_ADMIN` 的管理型代预约与普通用户本人预约严格分流。
     */
    @PostMapping("/proxy")
    public Result<ReservationResponse> createProxy(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ProxyReservationRequest request) {
        return Result.success(reservationService.createProxyReservation(principal.userId(), principal.role(), request));
    }

    /**
     * 设备管理员第一审接口。
     * <p>
     * 这里只负责承接一审动作并把当前操作者身份下传给服务层，
     * 真正的审批流转、通知发送和 workflow context 回包都由服务层统一编排，避免控制层复制业务判断。
     */
    @PostMapping("/{id}/audit")
    public Result<ReservationResponse> deviceAudit(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody AuditReservationRequest request) {
        return Result.success(reservationService.deviceApprove(reservationId, principal.userId(), principal.role(), request));
    }

    /**
     * 系统管理员第二审接口。
     * <p>
     * 双审批模式下，第二审会把预约推进到最终审批结论，因此服务层既要校验“双审不能同账号完成”，
     * 也要返回最终审批轨迹供前端待审批页和详情页直接复用。
     */
    @PostMapping("/{id}/system-audit")
    public Result<ReservationResponse> systemAudit(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody AuditReservationRequest request) {
        return Result.success(reservationService.systemApprove(reservationId, principal.userId(), principal.role(), request));
    }

    /**
     * 预约签到接口。
     * <p>
     * 前端会按签到窗口显式传入签到时间；服务层据此判断正常签到、超时签到或拒绝签到，
     * 并把最新签到结果与上下文字段一起回传给签到页继续渲染。
     */
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

    /**
     * 待人工处理预约的最终裁决接口。
     * <p>
     * 当预约已进入 `PENDING_MANUAL` 时，只有允许的管理角色才能在此接口内给出最终处理结果；
     * 服务层会统一落库状态、补齐 remark，并返回前端继续展示所需的动作回包上下文。
     */
    @PutMapping("/{id}/manual-process")
    public Result<ReservationResponse> manualProcess(
            @PathVariable("id") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ManualProcessRequest request) {
        return Result.success(reservationService.manualProcess(reservationId, principal.userId(), principal.role(), request));
    }
}
