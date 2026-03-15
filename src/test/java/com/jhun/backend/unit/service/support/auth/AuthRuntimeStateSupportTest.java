package com.jhun.backend.unit.service.support.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 认证运行时状态组件测试。
 * <p>
 * 该测试锁定 C-09 与 C-10 实际要清理的内存态数据，避免调度器只保留空入口却没有可验证的业务效果。
 */
class AuthRuntimeStateSupportTest {

    /**
     * 验证 C-09 会清理过期验证码、已过期刷新令牌和历史锁定记录，避免认证内存态无限膨胀。
     */
    @Test
    void shouldCleanupExpiredAuthArtifacts() {
        AuthRuntimeStateSupport support = new AuthRuntimeStateSupport();
        LocalDateTime now = LocalDateTime.of(2026, 3, 20, 3, 0);

        support.storeVerificationCode("expired@example.com", "111111", now.minusMinutes(1));
        support.storeVerificationCode("active@example.com", "222222", now.plusMinutes(5));
        support.recordRefreshToken("expired-token", "user-1", now.minusSeconds(1));
        support.recordRefreshToken("active-token", "user-1", now.plusHours(1));
        support.recordLoginFailure("expired-account", 5, now.minusMinutes(1));
        support.recordLoginFailure("locked-account", 5, now.plusMinutes(10));

        support.cleanupExpiredAuthArtifacts(now);

        assertFalse(support.hasVerificationCode("expired@example.com"));
        assertTrue(support.hasVerificationCode("active@example.com"));
        assertFalse(support.hasRefreshToken("expired-token"));
        assertTrue(support.hasRefreshToken("active-token"));
        assertFalse(support.hasLoginFailureState("expired-account"));
        assertTrue(support.hasLoginFailureState("locked-account"));
    }

    /**
     * 验证 C-10 只清理超时会话快照，不把仍在活跃窗口内的会话误删。
     */
    @Test
    void shouldCleanupIdleSessionsOnly() {
        AuthRuntimeStateSupport support = new AuthRuntimeStateSupport();
        LocalDateTime now = LocalDateTime.of(2026, 3, 20, 10, 0);

        support.recordSession("expired-session", "user-1", now.minusMinutes(31), now.minusMinutes(31));
        support.recordSession("active-session", "user-1", now.minusMinutes(5), now.minusMinutes(5));

        support.cleanupTimedOutSessions(now, 30);

        assertFalse(support.hasSession("expired-session"));
        assertTrue(support.hasSession("active-session"));
    }

    /**
     * 验证会话触达会刷新最近活跃时间；若首次触达时不存在快照，也要即时补建，保证 C-10 基于真实请求形成空闲语义。
     */
    @Test
    void shouldUpsertSessionWhenTouched() {
        AuthRuntimeStateSupport support = new AuthRuntimeStateSupport();
        LocalDateTime initialTime = LocalDateTime.of(2026, 3, 20, 9, 0);
        LocalDateTime touchedTime = initialTime.plusMinutes(25);

        support.touchOrCreateSession("access-token-1", "user-1", initialTime);
        support.touchOrCreateSession("access-token-1", "user-1", touchedTime);
        support.cleanupTimedOutSessions(initialTime.plusMinutes(45), 30);

        assertTrue(support.hasSession("access-token-1"));
        assertEquals(touchedTime, support.getSessionState("access-token-1").lastAccessAt());
    }
}
