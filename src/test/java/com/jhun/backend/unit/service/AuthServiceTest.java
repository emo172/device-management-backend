package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.auth.LoginRequest;
import com.jhun.backend.dto.auth.LoginResponse;
import com.jhun.backend.dto.auth.RegisterRequest;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.dto.auth.SendResetCodeRequest;
import com.jhun.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 认证服务测试。
 * <p>
 * 该测试锁定注册默认角色、账号登录入口、验证码重置密码和历史密码复用限制，
 * 防止认证链路实现时偏离三角色体系与密码历史约束。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    /**
     * 验证新注册用户默认绑定 USER 角色，并立即返回可用的登录结果。
     */
    @Test
    void shouldRegisterUserWithDefaultRoleUser() {
        RegisterRequest request = new RegisterRequest(
                "zhangsan",
                "Password123!",
                "zhangsan@example.com",
                "张三",
                "13800138000");

        LoginResponse response = authService.register(request);

        assertEquals("USER", response.role());
        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
    }

    /**
     * 验证登录支持使用邮箱作为账号标识，保护前端双入口登录契约。
     */
    @Test
    void shouldLoginWithEmail() {
        authService.register(new RegisterRequest(
                "lisi",
                "Password123!",
                "lisi@example.com",
                "李四",
                "13800138001"));

        LoginResponse response = authService.login(new LoginRequest("lisi@example.com", "Password123!"));

        assertEquals("lisi", response.username());
    }

    /**
     * 验证密码重置时不能复用历史密码，避免绕过密码历史治理规则。
     */
    @Test
    void shouldRejectReusedPasswordWhenResetting() {
        authService.register(new RegisterRequest(
                "wangwu",
                "Password123!",
                "wangwu@example.com",
                "王五",
                "13800138002"));
        authService.sendResetCode(new SendResetCodeRequest("wangwu@example.com"));

        assertThrows(BusinessException.class, () -> authService.resetPassword(new ResetPasswordRequest(
                "wangwu@example.com",
                "888888",
                "Password123!")));
    }
}
