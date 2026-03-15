package com.jhun.backend.scheduler.overdue;

import com.jhun.backend.service.OverdueService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-07 逾期限制释放任务。
 * <p>
 * 按当前阶段的最小闭环规则，仅当 RESTRICTED 用户已经不存在状态为 OVERDUE 的借还记录时，系统才自动解除限制；
 * FROZEN 不在自动释放范围内，必须继续保留给系统管理员处理。
 */
@Component
public class OverdueRestrictionReleaseProcessor {

    private final OverdueService overdueService;

    public OverdueRestrictionReleaseProcessor(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    /**
     * 按正式 Cron 触发自动释放任务。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void releaseCurrentRestrictions() {
        releaseAt(LocalDateTime.now());
    }

    /**
     * 按指定执行时间运行自动释放。
     *
     * @param executeTime 任务执行时间；为空时由服务层兜底为当前时间
     */
    public void releaseAt(LocalDateTime executeTime) {
        overdueService.releaseRestrictedUsers(executeTime);
    }
}
