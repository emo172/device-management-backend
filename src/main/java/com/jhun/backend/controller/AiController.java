package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.dto.ai.AiHistoryDetailResponse;
import com.jhun.backend.dto.ai.AiHistorySummaryResponse;
import com.jhun.backend.service.AiService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话控制器。
 * <p>
 * 当前仅向普通用户开放文本对话与本人历史查询接口；设备管理员和系统管理员都不能从该入口越过既有业务流程直接操作数据。
 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('USER')")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 发起一轮 AI 文本对话。
     *
     * @param principal 当前登录人
     * @param request 对话请求
     * @return AI 回复与历史 ID
     */
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody AiChatRequest request) {
        return Result.success(aiService.chat(principal.userId(), principal.role(), request));
    }

    /**
     * 查询当前登录人的 AI 历史列表。
     *
     * @param principal 当前登录人
     * @return 历史列表
     */
    @GetMapping("/history")
    public Result<List<AiHistorySummaryResponse>> listHistory(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(aiService.listHistory(principal.userId(), principal.role()));
    }

    /**
     * 查询当前登录人的一条 AI 历史详情。
     *
     * @param principal 当前登录人
     * @param historyId 历史记录 ID
     * @return 历史详情
     */
    @GetMapping("/history/{id}")
    public Result<AiHistoryDetailResponse> getHistoryDetail(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable("id") String historyId) {
        return Result.success(aiService.getHistoryDetail(principal.userId(), principal.role(), historyId));
    }
}
