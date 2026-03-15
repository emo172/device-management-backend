package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.mapper.PasswordHistoryMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.impl.AuthServiceImpl;
import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import com.jhun.backend.service.support.notification.EmailSender;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 认证服务实现单元测试。
 * <p>
 * 这里专门补服务层接线测试，防止后续把认证运行时组件的原子计数和过期判断绕开后，
 * 仍被纯组件级单测误判为“修复还在”。
 */
class AuthServiceImplUnitTest {

    /**
     * 验证服务层记录登录失败时会真正走认证运行时组件的原子累加路径，
     * 避免私有封装被后续改回“先读后写”而没有测试拦住。
     */
    @Test
    void shouldAccumulateLoginFailuresAtomicallyWhenServiceRecordsFailures() throws Exception {
        AuthRuntimeStateSupport runtimeStateSupport = new AuthRuntimeStateSupport();
        AuthServiceImpl authService = new AuthServiceImpl(
                mock(UserMapper.class),
                mock(RoleMapper.class),
                mock(PasswordHistoryMapper.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                mock(EmailSender.class),
                runtimeStateSupport);
        int concurrentAttempts = 12;
        CountDownLatch readyLatch = new CountDownLatch(concurrentAttempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentAttempts);

        try {
            for (int index = 0; index < concurrentAttempts; index++) {
                executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    ReflectionTestUtils.invokeMethod(authService, "recordLoginFailure", "concurrent@example.com");
                    return null;
                });
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();
        } finally {
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        }

        AuthRuntimeStateSupport.LoginFailureState state = runtimeStateSupport.getLoginFailureState("concurrent@example.com");
        assertEquals(concurrentAttempts, state.failureCount());
        assertNotNull(state.lockedUntil());
    }

    /**
     * 验证重置密码入口会委托认证运行时组件执行“到期即失效”的判断，
     * 避免服务层重新写一套边界逻辑后与 C-09 清理口径漂移。
     */
    @Test
    void shouldRejectResetPasswordWhenRuntimeSupportMarksCodeExpired() {
        UserMapper userMapper = mock(UserMapper.class);
        AuthRuntimeStateSupport runtimeStateSupport = mock(AuthRuntimeStateSupport.class);
        AuthServiceImpl authService = new AuthServiceImpl(
                userMapper,
                mock(RoleMapper.class),
                mock(PasswordHistoryMapper.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                mock(EmailSender.class),
                runtimeStateSupport);
        LocalDateTime expireAt = LocalDateTime.of(2026, 3, 20, 10, 0);
        AuthRuntimeStateSupport.VerificationCodeState verificationCodeState =
                new AuthRuntimeStateSupport.VerificationCodeState("123456", expireAt);

        when(runtimeStateSupport.getVerificationCodeState("expired@example.com")).thenReturn(verificationCodeState);
        when(runtimeStateSupport.isExpiredAtOrBefore(eq(expireAt), any(LocalDateTime.class))).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.resetPassword(new ResetPasswordRequest("expired@example.com", "123456", "Password123!")));

        assertEquals("验证码不存在或已过期", exception.getMessage());
        verify(runtimeStateSupport).isExpiredAtOrBefore(eq(expireAt), any(LocalDateTime.class));
        verify(userMapper, never()).findByEmail("expired@example.com");
    }
}
