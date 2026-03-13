package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.auth.ChangePasswordRequest;
import com.jhun.backend.dto.auth.CurrentUserResponse;
import com.jhun.backend.dto.auth.LoginRequest;
import com.jhun.backend.dto.auth.LoginResponse;
import com.jhun.backend.dto.auth.RegisterRequest;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.dto.auth.SendResetCodeRequest;
import com.jhun.backend.dto.auth.UpdateProfileRequest;
import com.jhun.backend.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与个人资料控制器。
 * <p>
 * 承载注册、登录、查询本人、修改资料、修改密码和密码重置等认证域接口，
 * 统一通过 {@link AuthService} 编排业务，避免控制层直接处理密码、令牌和历史密码规则。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册用户并返回登录结果。
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 登录并返回访问令牌与刷新令牌。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 查询当前登录用户资料。
     */
    @GetMapping("/me")
    public Result<CurrentUserResponse> me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(authService.getCurrentUser(principal.userId()));
    }

    /**
     * 更新当前登录用户资料。
     */
    @PutMapping("/profile")
    public Result<CurrentUserResponse> updateProfile(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody UpdateProfileRequest request) {
        return Result.success(authService.updateProfile(principal.userId(), request));
    }

    /**
     * 修改当前登录用户密码。
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return Result.success(null);
    }

    /**
     * 发送密码重置验证码。
     */
    @PostMapping("/verification-code")
    public Result<Void> sendResetCode(@RequestBody SendResetCodeRequest request) {
        authService.sendResetCode(request);
        return Result.success(null);
    }

    /**
     * 使用验证码重置密码。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success(null);
    }
}
