package com.jhun.backend.scheduler.overdue;

import com.jhun.backend.service.OverdueService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-06 逾期通知发送任务。
 * <p>
 * 每小时半点为尚未通知的逾期记录补发 OVERDUE_WARNING，并把 notification_sent 标记为已发送，
 * 避免管理端和统计端看到逾期事实存在但正式提醒缺失。
 */
@Component
public class OverdueNotificationProcessor {

    private final OverdueService overdueService;

    public OverdueNotificationProcessor(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    /**
     * 按正式 Cron 触发当前时间的逾期通知补发。
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void notifyCurrentOverdues() {
        notifyAt(LocalDateTime.now());
    }

    /**
     * 按指定执行时间补发逾期提醒。
     *
     * @param executeTime 任务执行时间；为空时由服务层兜底为当前时间
     */
    public void notifyAt(LocalDateTime executeTime) {
        overdueService.sendPendingOverdueNotifications(executeTime);
    }
}
