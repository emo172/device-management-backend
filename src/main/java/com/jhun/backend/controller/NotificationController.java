package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.notification.MarkAllReadResponse;
import com.jhun.backend.dto.notification.MarkReadResponse;
import com.jhun.backend.dto.notification.NotificationPageResponse;
import com.jhun.backend.dto.notification.NotificationResponse;
import com.jhun.backend.dto.notification.UnreadCountResponse;
import java.util.List;
import com.jhun.backend.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知控制器。
 * <p>
 * 为前端通知页提供通知列表、未读角标、单条已读和全部已读接口，已读能力严格限定为当前登录人的站内信记录。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<NotificationResponse>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(notificationService.listNotifications(principal.userId()));
    }

    /**
     * 通知分页列表接口。
     * <p>
     * 分页与服务端筛选作为独立扩展接口收敛，避免直接替换现有通知数组接口后打断当前前端主线的联调契约。
     */
    @GetMapping("/page")
    public Result<NotificationPageResponse> listPage(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "notificationType", required = false) String notificationType) {
        return Result.success(notificationService.listNotificationPage(principal.userId(), page, size, notificationType));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(notificationService.getUnreadCount(principal.userId()));
    }

    @PutMapping("/{id}/read")
    public Result<MarkReadResponse> markAsRead(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable("id") String notificationId) {
        return Result.success(notificationService.markAsRead(principal.userId(), notificationId));
    }

    @PutMapping("/read-all")
    public Result<MarkAllReadResponse> markAllAsRead(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(notificationService.markAllAsRead(principal.userId()));
    }
}
