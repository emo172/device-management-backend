package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.speech.AiSpeechTranscriptionResponse;
import com.jhun.backend.service.SpeechService;
import com.jhun.backend.service.support.speech.SpeechSynthesisResult;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 语音控制器。
 * <p>
 * 语音接口继续沿用 `/api/ai` 的 USER 权限边界，但与现有文本对话入口拆分为独立控制器，
 * 避免把录音上传和历史回复播报直接混入文本聊天契约。
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

    @GetMapping("/history/{id}/speech")
    public ResponseEntity<byte[]> synthesizeHistorySpeech(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable("id") String historyId) {
        SpeechSynthesisResult result = speechService.synthesizeHistorySpeech(principal.userId(), principal.role(), historyId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(result.audioBytes());
    }
}
