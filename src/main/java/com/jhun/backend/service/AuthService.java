package com.jhun.backend.service;

import com.jhun.backend.dto.auth.ChangePasswordRequest;
import com.jhun.backend.dto.auth.CurrentUserResponse;
import com.jhun.backend.dto.auth.LoginRequest;
import com.jhun.backend.dto.auth.LoginResponse;
import com.jhun.backend.dto.auth.RegisterRequest;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.dto.auth.SendResetCodeRequest;
import com.jhun.backend.dto.auth.UpdateProfileRequest;

/**
 * 认证服务。
 * <p>
 * 负责认证、个人资料、密码修改与密码重置等账号基础能力，禁止控制层直接拼接认证逻辑。
 */
public interface AuthService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser(String userId);

    CurrentUserResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    void sendResetCode(SendResetCodeRequest request);

    void resetPassword(ResetPasswordRequest request);
}
