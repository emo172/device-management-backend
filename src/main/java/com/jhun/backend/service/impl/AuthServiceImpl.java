package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.dto.auth.ChangePasswordRequest;
import com.jhun.backend.dto.auth.CurrentUserResponse;
import com.jhun.backend.dto.auth.LoginRequest;
import com.jhun.backend.dto.auth.LoginResponse;
import com.jhun.backend.dto.auth.RegisterRequest;
import com.jhun.backend.dto.auth.ResetPasswordRequest;
import com.jhun.backend.dto.auth.SendResetCodeRequest;
import com.jhun.backend.dto.auth.UpdateProfileRequest;
import com.jhun.backend.entity.PasswordHistory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.PasswordHistoryMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.AuthService;
import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import com.jhun.backend.service.support.notification.EmailSender;
import com.jhun.backend.util.UuidUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现。
 * <p>
 * 当前阶段先完成注册、登录、个人资料维护、验证码重置密码和密码历史校验等核心链路，
 * 并用内存态辅助结构兜住登录失败锁定与验证码校验，供后续 Redis 化前保持业务闭环。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE_NAME = "USER";
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int RESET_CODE_EXPIRE_MINUTES = 5;

    /**
     * 密码重置验证码固定为 6 位数字，既满足前端输入体验，也避免使用可预测常量。
     */
    private static final int RESET_CODE_BOUND = 1_000_000;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailSender emailSender;
    private final AuthRuntimeStateSupport authRuntimeStateSupport;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordHistoryMapper passwordHistoryMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            EmailSender emailSender,
            AuthRuntimeStateSupport authRuntimeStateSupport) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailSender = emailSender;
        this.authRuntimeStateSupport = authRuntimeStateSupport;
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userMapper.findByAccount(request.username()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.findByAccount(request.email()) != null) {
            throw new BusinessException("邮箱已存在或与现有用户名冲突");
        }

        Role defaultRole = roleMapper.findByName(DEFAULT_ROLE_NAME);
        if (defaultRole == null) {
            throw new BusinessException("默认用户角色不存在");
        }

        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoleId(defaultRole.getId());
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        savePasswordHistory(user.getId(), user.getPasswordHash());

        return buildLoginResponse(user, defaultRole.getName());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        assertAccountNotLocked(request.account());
        User user = userMapper.findByAccount(request.account());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordLoginFailure(request.account());
            throw new BusinessException("用户名、邮箱或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账户已被禁用");
        }
        clearLoginFailure(request.account());
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        Role role = roleMapper.selectById(user.getRoleId());
        return buildLoginResponse(user, role == null ? DEFAULT_ROLE_NAME : role.getName());
    }

    @Override
    public CurrentUserResponse getCurrentUser(String userId) {
        User user = userMapper.selectCurrentUserProfile(userId);
        if (user == null) {
            throw new BusinessException("当前用户不存在");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        return toCurrentUserResponse(user, role == null ? DEFAULT_ROLE_NAME : role.getName());
    }

    @Override
    @Transactional
    public CurrentUserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = mustFindUserById(userId);
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        userMapper.updateById(user);
        Role role = roleMapper.selectById(user.getRoleId());
        return toCurrentUserResponse(user, role == null ? DEFAULT_ROLE_NAME : role.getName());
    }

    @Override
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = mustFindUserById(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("旧密码不正确");
        }
        validatePasswordNotReused(userId, request.newPassword());
        updatePassword(user, request.newPassword());
    }

    @Override
    public void sendResetCode(SendResetCodeRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null) {
            throw new BusinessException("邮箱未注册");
        }
        String verificationCode = generateResetCode();
        authRuntimeStateSupport.storeVerificationCode(
                request.email(),
                verificationCode,
                LocalDateTime.now().plusMinutes(RESET_CODE_EXPIRE_MINUTES));

        /*
         * 重置接口对匿名用户开放，因此验证码不能只存在服务端内存而不经通道下发。
         * 这里先统一走邮件发送抽象，后续接入真实 SMTP 时无需改动认证主链路。
         */
        try {
            emailSender.send(
                    request.email(),
                    "智能设备管理系统密码重置验证码",
                    "您的验证码为：" + verificationCode + "，" + RESET_CODE_EXPIRE_MINUTES + " 分钟内有效。");
        }
        catch (RuntimeException exception) {
            authRuntimeStateSupport.removeVerificationCode(request.email());
            throw new BusinessException("验证码发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AuthRuntimeStateSupport.VerificationCodeState verificationCodeState =
                authRuntimeStateSupport.getVerificationCodeState(request.email());
        LocalDateTime now = LocalDateTime.now();
        if (verificationCodeState == null
                || authRuntimeStateSupport.isExpiredAtOrBefore(verificationCodeState.expireAt(), now)) {
            throw new BusinessException("验证码不存在或已过期");
        }
        if (!verificationCodeState.code().equals(request.verificationCode())) {
            throw new BusinessException("验证码错误");
        }
        User user = userMapper.findByEmail(request.email());
        if (user == null) {
            throw new BusinessException("邮箱未注册");
        }
        validatePasswordNotReused(user.getId(), request.newPassword());
        updatePassword(user, request.newPassword());
        authRuntimeStateSupport.removeVerificationCode(request.email());
    }

    private CurrentUserResponse toCurrentUserResponse(User user, String roleName) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRealName(), user.getPhone(), roleName);
    }

    private LoginResponse buildLoginResponse(User user, String roleName) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), roleName);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername(), roleName);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refreshTokenExpireAt = jwtTokenProvider.parseClaims(refreshToken)
                .getExpiration()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        /*
         * 这里分别记录 refresh token 快照与 access token 对应的会话快照，仅用于运维态清理和问题排查，
         * 不参与受保护接口的令牌校验。会话快照必须与过滤器里用于触达的 access token 保持同一键空间，
         * 否则 C-10 会看到“登录时一套键、访问时另一套键”的并行快照，导致空闲治理口径失真。
         */
        authRuntimeStateSupport.recordRefreshToken(refreshToken, user.getId(), refreshTokenExpireAt);
        authRuntimeStateSupport.recordSession(accessToken, user.getId(), now, now);

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                roleName,
                accessToken,
                refreshToken);
    }

    private User mustFindUserById(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void validatePasswordNotReused(String userId, String rawPassword) {
        List<PasswordHistory> histories = passwordHistoryMapper.findByUserId(userId);
        boolean reused = histories.stream().anyMatch(history -> passwordEncoder.matches(rawPassword, history.getPasswordHash()));
        if (reused) {
            throw new BusinessException("新密码不能与历史密码重复");
        }
    }

    private void updatePassword(User user, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        user.setPasswordHash(encodedPassword);
        userMapper.updateById(user);
        savePasswordHistory(user.getId(), encodedPassword);
    }

    private void savePasswordHistory(String userId, String passwordHash) {
        PasswordHistory passwordHistory = new PasswordHistory();
        passwordHistory.setId(UuidUtil.randomUuid());
        passwordHistory.setUserId(userId);
        passwordHistory.setPasswordHash(passwordHash);
        passwordHistoryMapper.insert(passwordHistory);
    }

    private void assertAccountNotLocked(String account) {
        AuthRuntimeStateSupport.LoginFailureState state = authRuntimeStateSupport.getLoginFailureState(account);
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("登录失败次数过多，请 30 分钟后再试");
        }
    }

    private void recordLoginFailure(String account) {
        authRuntimeStateSupport.incrementLoginFailure(account, MAX_FAILED_LOGIN_ATTEMPTS, LocalDateTime.now().plusMinutes(30));
    }

    private void clearLoginFailure(String account) {
        authRuntimeStateSupport.clearLoginFailure(account);
    }

    /**
     * 生成 6 位随机验证码。
     * <p>
     * 这里显式使用安全随机数，避免匿名重置接口退化为可枚举的固定验证码入口。
     */
    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(RESET_CODE_BOUND));
    }

}
