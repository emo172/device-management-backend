package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.auth.LoginRequest;
import com.jhun.backend.dto.auth.LoginResponse;
import com.jhun.backend.dto.auth.RegisterRequest;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.dto.auth.SendResetCodeRequest;
import com.jhun.backend.service.AuthService;
import com.jhun.backend.service.impl.AuthServiceImpl;
import com.jhun.backend.service.support.notification.EmailSender;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 认证服务测试。
 * <p>
 * 该测试锁定注册默认角色、账号登录入口、验证码重置密码和历史密码复用限制，
 * 防止认证链路实现时偏离三角色体系与密码历史约束。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_service_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthServiceImpl authServiceImpl;

    @MockitoBean
    private EmailSender emailSender;

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

    /**
     * 验证重置验证码必须按请求动态生成，避免匿名重置接口落成固定口令入口。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateRandomResetCodeInsteadOfUsingFixedCode() {
        authService.register(new RegisterRequest(
                "zhaoba",
                "Password123!",
                "zhaoba@example.com",
                "赵八",
                "13800138008"));

        authService.sendResetCode(new SendResetCodeRequest("zhaoba@example.com"));
        Map<String, ?> verificationCodeStates =
                (Map<String, ?>) ReflectionTestUtils.getField(authServiceImpl, "verificationCodeStates");
        Object firstState = verificationCodeStates.get("zhaoba@example.com");
        String firstCode = (String) ReflectionTestUtils.invokeMethod(firstState, "code");

        authService.sendResetCode(new SendResetCodeRequest("zhaoba@example.com"));
        Object secondState = verificationCodeStates.get("zhaoba@example.com");
        String secondCode = (String) ReflectionTestUtils.invokeMethod(secondState, "code");

        verify(emailSender, org.mockito.Mockito.times(2)).send(eq("zhaoba@example.com"), eq("智能设备管理系统密码重置验证码"), contains("您的验证码为："));
        assertNotEquals("888888", firstCode);
        assertNotEquals(firstCode, secondCode);
    }

    /**
     * 验证注册时要阻止“邮箱值撞上已有用户名”，避免登录入口 OR 条件命中多条记录。
     */
    @Test
    void shouldRejectEmailThatConflictsWithExistingUsername() {
        authService.register(new RegisterRequest(
                "conflict-user",
                "Password123!",
                "origin@example.com",
                "冲突用户",
                "13800138009"));

        assertThrows(BusinessException.class, () -> authService.register(new RegisterRequest(
                "another-user",
                "Password123!",
                "conflict-user",
                "后续用户",
                "13800138010")));
    }

    /**
     * 验证邮件发送失败时不能把验证码留在服务端内存中，避免用户收不到验证码却占住有效窗口。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveResetCodeStateWhenEmailDeliveryFails() {
        authService.register(new RegisterRequest(
                "shiyi",
                "Password123!",
                "shiyi@example.com",
                "十一",
                "13800138012"));

        doThrow(new IllegalStateException("smtp unavailable"))
                .when(emailSender)
                .send(eq("shiyi@example.com"), eq("智能设备管理系统密码重置验证码"), contains("您的验证码为："));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.sendResetCode(new SendResetCodeRequest("shiyi@example.com")));

        Map<String, ?> verificationCodeStates =
                (Map<String, ?>) ReflectionTestUtils.getField(authServiceImpl, "verificationCodeStates");
        assertEquals("验证码发送失败，请稍后重试", exception.getMessage());
        assertFalse(verificationCodeStates.containsKey("shiyi@example.com"));
    }
}
