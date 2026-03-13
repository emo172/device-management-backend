package com.jhun.backend.scheduler.system;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会话超时检查任务。
 * <p>
 * 当前阶段先建立定时任务骨架，对应计划中的 C-10，后续接入真实会话存储后在此执行超时清理。
 */
@Component
public class SessionTimeoutProcessor {

    /**
     * 预留会话超时检查入口，对应任务编号 C-10。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void processSessionTimeout() {
        // 当前阶段只建立调度入口，具体会话超时逻辑后续接入。
    }
}
