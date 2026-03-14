package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.borrow.BorrowRecordResponse;
import com.jhun.backend.dto.borrow.ConfirmBorrowRequest;
import com.jhun.backend.service.BorrowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
