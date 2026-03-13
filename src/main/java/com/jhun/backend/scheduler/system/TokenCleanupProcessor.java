package com.jhun.backend.scheduler.system;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token 清理任务。
 * <p>
 * 当前阶段先保留定时任务入口，后续接入 Redis 或持久化 token 存储后在此补足真实清理逻辑。
 */
@Component
public class TokenCleanupProcessor {

    /**
     * 预留 Token 清理调度入口，对应任务编号 C-09。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredTokens() {
        // 当前阶段只建立调度入口，具体清理逻辑在接入 token 存储后补齐。
    }
}
