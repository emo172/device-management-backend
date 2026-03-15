package com.jhun.backend.scheduler.system;

import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会话超时检查任务。
 * <p>
 * 当前阶段负责清理认证运行时中的空闲会话快照，对应计划中的 C-10。
 * 该任务只治理观测型会话索引，不把会话快照存在变成访问接口的硬前置。
 */
@Component
public class SessionTimeoutProcessor {

    private final AuthRuntimeStateSupport authRuntimeStateSupport;

    public SessionTimeoutProcessor(AuthRuntimeStateSupport authRuntimeStateSupport) {
        this.authRuntimeStateSupport = authRuntimeStateSupport;
    }

    /**
     * 执行 C-10 会话空闲清理。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void processSessionTimeout() {
        authRuntimeStateSupport.cleanupTimedOutSessions();
    }
}
