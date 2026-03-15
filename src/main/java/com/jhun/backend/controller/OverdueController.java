package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.overdue.OverdueRecordPageResponse;
import com.jhun.backend.dto.overdue.OverdueRecordResponse;
import com.jhun.backend.dto.overdue.ProcessOverdueRequest;
import com.jhun.backend.service.OverdueService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 逾期控制器。
 * <p>
 * 为逾期管理页提供列表、详情和处理入口，控制层只负责透传当前登录人的 userId 与 role，
 * 具体的“普通用户只能看本人”“只有 DEVICE_ADMIN 能处理逾期”等业务边界统一由服务层裁决。
 */
@RestController
@RequestMapping("/api/overdue-records")
public class OverdueController {

    private final OverdueService overdueService;

    public OverdueController(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    /**
     * 查询逾期记录列表。
     *
     * @param principal 当前认证主体
     * @param page 页码
     * @param size 每页大小
     * @param processingStatus 可选处理状态筛选
     * @return 逾期记录分页结果
     */
    @GetMapping
    public Result<OverdueRecordPageResponse> list(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "processingStatus", required = false) String processingStatus) {
        return Result.success(overdueService.listOverdueRecords(principal.userId(), principal.role(), page, size, processingStatus));
    }

    /**
     * 查询逾期记录详情。
     *
     * @param overdueRecordId 逾期记录 ID
     * @param principal 当前认证主体
     * @return 逾期记录详情
     */
    @GetMapping("/{id}")
    public Result<OverdueRecordResponse> detail(
            @PathVariable("id") String overdueRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(overdueService.getOverdueRecordDetail(overdueRecordId, principal.userId(), principal.role()));
    }

    /**
     * 处理逾期记录。
     *
     * @param overdueRecordId 逾期记录 ID
     * @param principal 当前认证主体
     * @param request 处理请求
     * @return 更新后的逾期记录响应
     */
    @PostMapping("/{id}/process")
    public Result<OverdueRecordResponse> process(
            @PathVariable("id") String overdueRecordId,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ProcessOverdueRequest request) {
        return Result.success(overdueService.processOverdueRecord(overdueRecordId, principal.userId(), principal.role(), request));
    }
}
