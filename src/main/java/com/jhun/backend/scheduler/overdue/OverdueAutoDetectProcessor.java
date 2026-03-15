package com.jhun.backend.scheduler.overdue;

import com.jhun.backend.service.OverdueService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-05 逾期自动识别任务。
 * <p>
 * 每小时扫描一次 borrow_record.expected_return_time，自动补齐新逾期、刷新已逾期时长，并驱动 RESTRICTED / FROZEN 分段策略。
 * 测试场景不依赖真实调度时间，而是直接调用 {@link #detectAt(LocalDateTime)} 进行验证。
 */
@Component
public class OverdueAutoDetectProcessor {

    private final OverdueService overdueService;

    public OverdueAutoDetectProcessor(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    /**
     * 按正式 Cron 触发当前时间的逾期检测。
     * <p>
     * 生产环境由调度器调用该入口，测试环境则通过 {@link #detectAt(LocalDateTime)} 传入固定时间验证边界。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void detectCurrentOverdues() {
        detectAt(LocalDateTime.now());
    }

    /**
     * 按指定参考时间执行逾期检测。
     *
     * @param referenceTime 业务参考时间；为空时由服务层兜底为当前时间
     */
    public void detectAt(LocalDateTime referenceTime) {
        overdueService.detectOverdues(referenceTime);
    }
}
