package com.jhun.backend.unit.scheduler.system;

import static org.mockito.Mockito.verify;

import com.jhun.backend.scheduler.system.TokenCleanupProcessor;
import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Token 清理调度器测试。
 * <p>
 * 该测试保护 C-09 不会退化为空方法，至少要把定时入口连接到真实的认证运行时状态清理逻辑。
 */
@ExtendWith(MockitoExtension.class)
class TokenCleanupProcessorTest {

    @Mock
    private AuthRuntimeStateSupport authRuntimeStateSupport;

    /**
     * 验证调度器执行时会触发认证运行时状态清理。
     */
    @Test
    void shouldDelegateExpiredArtifactCleanup() {
        TokenCleanupProcessor processor = new TokenCleanupProcessor(authRuntimeStateSupport);

        processor.cleanExpiredTokens();

        verify(authRuntimeStateSupport).cleanupExpiredAuthArtifacts();
    }
}
