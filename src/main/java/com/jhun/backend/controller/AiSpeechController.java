package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.SpeechService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音控制器。
 * <p>
 * 语音接口继续沿用 `/api/ai` 的 USER 权限边界，但在 ASR-only 改造后只保留录音转写入口。
 * 当前控制层正式合同固定为：接收浏览器上传的 PCM-WAV 录音，服务层统一走 WAV 解析 + 讯飞转写主链路，
 * 并向前端返回稳定的最终 transcript，而不是暴露上游 WebSocket 中间帧。
 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('USER')")
public class AiSpeechController {

    private final SpeechService speechService;

    public AiSpeechController(SpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping(value = "/speech/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AiSpeechTranscriptionResponse> transcribe(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @ModelAttribute("file") MultipartFile file) {
        return Result.success(speechService.transcribe(principal.userId(), principal.role(), file));
    }
}
