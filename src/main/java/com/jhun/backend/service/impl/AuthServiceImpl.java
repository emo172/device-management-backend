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
import com.jhun.backend.service.support.notification.EmailSender;
import com.jhun.backend.util.UuidUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, LoginFailureState> loginFailureStates = new ConcurrentHashMap<>();
    private final Map<String, VerificationCodeState> verificationCodeStates = new ConcurrentHashMap<>();

    public AuthServiceImpl(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordHistoryMapper passwordHistoryMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            EmailSender emailSender) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailSender = emailSender;
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
        verificationCodeStates.put(
                request.email(),
                new VerificationCodeState(verificationCode, LocalDateTime.now().plusMinutes(RESET_CODE_EXPIRE_MINUTES)));

        /*
         * 重置接口对匿名用户开放，因此验证码不能只存在服务端内存而不经通道下发。
         * 这里先统一走邮件发送抽象，后续接入真实 SMTP 时无需改动认证主链路。
         */
        emailSender.send(
                request.email(),
                "智能设备管理系统密码重置验证码",
                "您的验证码为：" + verificationCode + "，" + RESET_CODE_EXPIRE_MINUTES + " 分钟内有效。");
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationCodeState verificationCodeState = verificationCodeStates.get(request.email());
        if (verificationCodeState == null || verificationCodeState.expireAt().isBefore(LocalDateTime.now())) {
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
        verificationCodeStates.remove(request.email());
    }

    private CurrentUserResponse toCurrentUserResponse(User user, String roleName) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRealName(), user.getPhone(), roleName);
    }

    private LoginResponse buildLoginResponse(User user, String roleName) {
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                roleName,
                jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), roleName),
                jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername(), roleName));
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
        LoginFailureState state = loginFailureStates.get(account);
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("登录失败次数过多，请 30 分钟后再试");
        }
    }

    private void recordLoginFailure(String account) {
        LoginFailureState currentState = loginFailureStates.getOrDefault(account, new LoginFailureState(0, null));
        int nextFailureCount = currentState.failureCount() + 1;
        LocalDateTime lockedUntil = nextFailureCount >= MAX_FAILED_LOGIN_ATTEMPTS ? LocalDateTime.now().plusMinutes(30) : null;
        loginFailureStates.put(account, new LoginFailureState(nextFailureCount, lockedUntil));
    }

    private void clearLoginFailure(String account) {
        loginFailureStates.remove(account);
    }

    /**
     * 生成 6 位随机验证码。
     * <p>
     * 这里显式使用安全随机数，避免匿名重置接口退化为可枚举的固定验证码入口。
     */
    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(RESET_CODE_BOUND));
    }

    /** 登录失败状态快照。 */
    private record LoginFailureState(int failureCount, LocalDateTime lockedUntil) {
    }

    /** 验证码状态快照。 */
    private record VerificationCodeState(String code, LocalDateTime expireAt) {
    }
}
