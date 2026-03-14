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

    @PostMapping("/{reservationId}/confirm-borrow")
    public Result<BorrowRecordResponse> confirmBorrow(
            @PathVariable("reservationId") String reservationId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) ConfirmBorrowRequest request) {
        return Result.success(borrowService.confirmBorrow(reservationId, principal.userId(), principal.role(), request));
    }

    @PostMapping("/{borrowRecordId}/confirm-return")
    public Result<BorrowRecordResponse> confirmReturn(
            @PathVariable("borrowRecordId") String borrowRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) ConfirmReturnRequest request) {
        return Result.success(borrowService.confirmReturn(borrowRecordId, principal.userId(), principal.role(), request));
    }

    @GetMapping
    public Result<BorrowRecordPageResponse> list(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(borrowService.listBorrowRecords(principal.userId(), principal.role(), page, size, status));
    }

    @GetMapping("/{id}")
    public Result<BorrowRecordResponse> detail(
            @PathVariable("id") String borrowRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(borrowService.getBorrowRecordDetail(borrowRecordId, principal.userId(), principal.role()));
    }
}
