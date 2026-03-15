package com.jhun.backend.scheduler.system;

import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token 清理任务。
 * <p>
 * 当前阶段负责清理认证运行时内存态中的过期验证码、刷新令牌和失效锁定记录，
 * 避免这些快照在长期运行中持续堆积。
 */
@Component
public class TokenCleanupProcessor {

    private final AuthRuntimeStateSupport authRuntimeStateSupport;

    public TokenCleanupProcessor(AuthRuntimeStateSupport authRuntimeStateSupport) {
        this.authRuntimeStateSupport = authRuntimeStateSupport;
    }

    /**
     * 执行 C-09 Token 清理。
     * <p>
     * 当前不清理访问令牌本身，因为访问令牌仍是纯 JWT 无状态校验；这里清理的是认证运行时辅助状态。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredTokens() {
        authRuntimeStateSupport.cleanupExpiredAuthArtifacts();
    }
}
