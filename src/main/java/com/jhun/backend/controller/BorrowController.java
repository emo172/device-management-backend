package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.borrow.BorrowRecordPageResponse;
import com.jhun.backend.dto.borrow.BorrowRecordResponse;
import com.jhun.backend.dto.borrow.ConfirmBorrowRequest;
import com.jhun.backend.dto.borrow.ConfirmReturnRequest;
import com.jhun.backend.service.BorrowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 借还控制器。
 * <p>
 * 对外暴露借用确认、归还确认、列表与详情接口，承接借还管理页面与借还记录页的联调入口。
 */
@RestController
@RequestMapping("/api/borrow-records")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    /**
     * 借用确认接口。
     * <p>
     * 该入口只负责把当前登录人的身份信息透传给服务层，由服务层统一执行“仅 DEVICE_ADMIN 可操作”与预约/签到状态校验；
     * 控制层不直接改设备状态，避免绕过借还事务编排导致 borrow_record、device、device_status_log 不一致。
     *
     * @param reservationId 预约 ID
     * @param principal 当前认证主体
     * @param request 借用确认请求
     * @return 借还记录响应
     */
    @PostMapping("/{reservationId}/confirm-borrow")
    public Result<BorrowRecordResponse> confirmBorrow(
            @PathVariable("reservationId") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) ConfirmBorrowRequest request) {
        return Result.success(borrowService.confirmBorrow(reservationId, principal.userId(), principal.role(), request));
    }

    /**
     * 归还确认接口。
     * <p>
     * 该入口把“当前谁在归还哪条正式借还记录”交由服务层处理，服务层会继续校验 DEVICE_ADMIN 边界并驱动设备状态从 BORROWED 恢复到 AVAILABLE；
     * 这样可以确保所有归还动作都经过正式借还闭环，而不是通过设备管理接口手工回退状态。
     *
     * @param borrowRecordId 借还记录 ID
     * @param principal 当前认证主体
     * @param request 归还确认请求
     * @return 更新后的借还记录响应
     */
    @PostMapping("/{borrowRecordId}/confirm-return")
    public Result<BorrowRecordResponse> confirmReturn(
            @PathVariable("borrowRecordId") String borrowRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) ConfirmReturnRequest request) {
        return Result.success(borrowService.confirmReturn(borrowRecordId, principal.userId(), principal.role(), request));
    }

    /**
     * 借还记录列表接口。
     * <p>
     * 控制层始终把当前登录人的 userId 和 role 一并传给服务层，由服务层统一收敛“普通用户只看本人、管理角色看管理视角”的过滤规则；
     * 这样可以避免前端通过更换查询参数绕过权限边界。
     *
     * @param principal 当前认证主体
     * @param page 页码
     * @param size 每页大小
     * @param status 可选状态筛选
     * @return 借还记录分页结果
     */
    @GetMapping
    public Result<BorrowRecordPageResponse> list(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(borrowService.listBorrowRecords(principal.userId(), principal.role(), page, size, status));
    }

    /**
     * 借还记录详情接口。
     * <p>
     * 详情接口沿用与列表接口相同的权限口径，防止普通用户通过已知 ID 直接查看他人借还详情；
     * 真正的权限裁决仍在服务层统一执行，控制层只负责承接请求与返回统一响应。
     *
     * @param borrowRecordId 借还记录 ID
     * @param principal 当前认证主体
     * @return 借还记录详情
     */
    @GetMapping("/{id}")
    public Result<BorrowRecordResponse> detail(
            @PathVariable("id") String borrowRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(borrowService.getBorrowRecordDetail(borrowRecordId, principal.userId(), principal.role()));
    }
}
