package com.jhun.backend.unit.scheduler.system;

import static org.mockito.Mockito.verify;

import com.jhun.backend.scheduler.system.SessionTimeoutProcessor;
import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 会话超时调度器测试。
 * <p>
 * 该测试保护 C-10 不会只保留 cron 壳子，而是会真正驱动会话快照超时清理。
 */
@ExtendWith(MockitoExtension.class)
class SessionTimeoutProcessorTest {

    @Mock
    private AuthRuntimeStateSupport authRuntimeStateSupport;

    /**
     * 验证调度器执行时会触发空闲会话清理。
     */
    @Test
    void shouldDelegateSessionTimeoutCleanup() {
        SessionTimeoutProcessor processor = new SessionTimeoutProcessor(authRuntimeStateSupport);

        processor.processSessionTimeout();

        verify(authRuntimeStateSupport).cleanupTimedOutSessions();
    }
}
