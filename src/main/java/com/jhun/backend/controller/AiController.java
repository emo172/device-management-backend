package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.config.speech.SpeechProperties;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.dto.ai.AiCapabilitiesResponse;
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
 * 当前仅向普通用户开放 AI 能力查询、文本对话与本人历史查询接口；
 * 设备管理员和系统管理员都不能从该入口越过既有业务流程直接操作数据。
 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('USER')")
public class AiController {

    private final AiService aiService;
    private final AiRuntimeProperties aiRuntimeProperties;
    private final SpeechProperties speechProperties;

    public AiController(
            AiService aiService,
            AiRuntimeProperties aiRuntimeProperties,
            SpeechProperties speechProperties) {
        this.aiService = aiService;
        this.aiRuntimeProperties = aiRuntimeProperties;
        this.speechProperties = speechProperties;
    }

    /**
     * 查询当前运行时可用的 AI 能力开关。
     * <p>
     * 该接口只回传前端当前联调真正需要的两个布尔值，并且 `speechEnabled` 必须表示“现在真的可以走通语音转写链路”，
     * 不能只回显 `speech.enabled` 配置本身；否则前端会把录音入口展示出来，却在用户第一次上传时才发现凭据或 provider 不可用。
     * 因此这里继续只暴露最小布尔真相，不把 provider、密钥、区域等内部运行时细节泄露给公开接口。
     *
     * @return AI 能力最小响应
     */
    @GetMapping("/capabilities")
    public Result<AiCapabilitiesResponse> getCapabilities() {
        return Result.success(new AiCapabilitiesResponse(
                aiRuntimeProperties.isChatEnabled(),
                speechProperties.isTranscriptionAvailable()));
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
