package com.jhun.backend.service.support.auth;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 认证运行时状态支撑组件。
 * <p>
 * 当前认证链仍以 JWT 无状态访问为主，但登录失败锁定、密码重置验证码、刷新令牌快照和会话快照
 * 都属于需要被定时任务治理的运行时状态。该组件统一托管这些内存态数据，避免状态散落在服务实现里，
 * 同时为 C-09 与 C-10 提供可验证的真实清理目标。
 */
@Component
public class AuthRuntimeStateSupport {

    /** 登录失败状态快照，按账号维度记录锁定信息。 */
    private final Map<String, LoginFailureState> loginFailureStates = new ConcurrentHashMap<>();

    /** 密码重置验证码快照，按邮箱维度记录验证码与有效期。 */
    private final Map<String, VerificationCodeState> verificationCodeStates = new ConcurrentHashMap<>();

    /**
     * 刷新令牌快照。
     * <p>
     * 当前系统尚未把 refresh token 做成访问鉴权硬前置，但需要保留快照用于运维排查与过期清理，
     * 且不能影响现有大量直接由 `JwtTokenProvider` 造 token 的测试和受保护接口访问。
     */
    private final Map<String, RefreshTokenState> refreshTokenStates = new ConcurrentHashMap<>();

    /**
     * 会话快照。
     * <p>
     * 这里只作为空闲会话治理和运维观测的内存索引，不参与访问令牌校验，避免破坏现有安全过滤链约定。
     */
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    /**
     * 读取登录失败状态。
     *
     * @param account 登录账号
     * @return 登录失败状态，若不存在则返回 {@code null}
     */
    public LoginFailureState getLoginFailureState(String account) {
        return loginFailureStates.get(account);
    }

    /**
     * 写入登录失败状态。
     *
     * @param account 登录账号
     * @param failureCount 当前失败次数
     * @param lockedUntil 锁定截止时间，可为空
     */
    public void recordLoginFailure(String account, int failureCount, LocalDateTime lockedUntil) {
        loginFailureStates.put(account, new LoginFailureState(failureCount, lockedUntil));
    }

    /**
     * 原子递增登录失败次数。
     * <p>
     * 登录失败可能被多个并发请求同时触发；若仍采用“先读后写”的方式，失败次数会被后写请求覆盖，
     * 进而延后锁定触发时间。这里统一用 `compute` 在状态容器内原子完成累加与锁定计算。
     *
     * @param account 登录账号
     * @param maxFailedAttempts 触发锁定的最大失败次数
     * @param lockedUntil 达到阈值后的锁定截止时间
     * @return 更新后的登录失败状态
     */
    public LoginFailureState incrementLoginFailure(String account, int maxFailedAttempts, LocalDateTime lockedUntil) {
        return loginFailureStates.compute(account, (ignored, currentState) -> {
            int nextFailureCount = currentState == null ? 1 : currentState.failureCount() + 1;
            LocalDateTime nextLockedUntil = nextFailureCount >= maxFailedAttempts ? lockedUntil : null;
            return new LoginFailureState(nextFailureCount, nextLockedUntil);
        });
    }

    /**
     * 清除登录失败状态。
     *
     * @param account 登录账号
     */
    public void clearLoginFailure(String account) {
        loginFailureStates.remove(account);
    }

    /**
     * 保存验证码状态。
     *
     * @param email 邮箱
     * @param code 验证码
     * @param expireAt 过期时间
     */
    public void storeVerificationCode(String email, String code, LocalDateTime expireAt) {
        verificationCodeStates.put(email, new VerificationCodeState(code, expireAt));
    }

    /**
     * 读取验证码状态。
     *
     * @param email 邮箱
     * @return 验证码状态，若不存在则返回 {@code null}
     */
    public VerificationCodeState getVerificationCodeState(String email) {
        return verificationCodeStates.get(email);
    }

    /**
     * 删除验证码状态。
     *
     * @param email 邮箱
     */
    public void removeVerificationCode(String email) {
        verificationCodeStates.remove(email);
    }

    /**
     * 记录刷新令牌快照。
     *
     * @param refreshToken 刷新令牌字符串
     * @param userId 用户 ID
     * @param expireAt 过期时间
     */
    public void recordRefreshToken(String refreshToken, String userId, LocalDateTime expireAt) {
        refreshTokenStates.put(refreshToken, new RefreshTokenState(userId, expireAt));
    }

    /**
     * 记录会话快照。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param createdAt 创建时间
     * @param lastAccessAt 最近访问时间
     */
    public void recordSession(String sessionId, String userId, LocalDateTime createdAt, LocalDateTime lastAccessAt) {
        sessionStates.put(sessionId, new SessionState(userId, createdAt, lastAccessAt));
    }

    /**
     * 刷新会话最近访问时间。
     *
     * @param sessionId 会话 ID
     * @param accessAt 最近访问时间
     */
    public void touchSession(String sessionId, LocalDateTime accessAt) {
        SessionState currentState = sessionStates.get(sessionId);
        if (currentState != null) {
            sessionStates.put(sessionId, new SessionState(currentState.userId(), currentState.createdAt(), accessAt));
        }
    }

    /**
     * 触达或补建会话快照。
     * <p>
     * C-10 的“空闲会话”语义必须基于真实请求访问形成，因此这里允许在首次看到某个稳定会话键时即时建档，
     * 后续每次访问都只刷新最近活跃时间。这样既不会把会话存在变成鉴权硬前置，也能让空闲清理具备真实业务含义。
     *
     * @param sessionId 可稳定标识当前访问链路的会话键
     * @param userId 当前访问用户 ID
     * @param accessAt 本次访问时间
     */
    public void touchOrCreateSession(String sessionId, String userId, LocalDateTime accessAt) {
        sessionStates.compute(sessionId, (key, currentState) -> currentState == null
                ? new SessionState(userId, accessAt, accessAt)
                : new SessionState(currentState.userId(), currentState.createdAt(), accessAt));
    }

    /**
     * 执行 C-09 清理逻辑。
     * <p>
     * 清理范围只包含运行时过期状态：已过期的验证码、已过期的刷新令牌、已解除锁定意义的登录失败记录。
     */
    public void cleanupExpiredAuthArtifacts() {
        cleanupExpiredAuthArtifacts(LocalDateTime.now());
    }

    /**
     * 执行 C-09 清理逻辑的可测重载。
     *
     * @param referenceTime 当前参考时间
     */
    public void cleanupExpiredAuthArtifacts(LocalDateTime referenceTime) {
        verificationCodeStates.entrySet().removeIf(entry -> isExpiredAtOrBefore(entry.getValue().expireAt(), referenceTime));
        refreshTokenStates.entrySet().removeIf(entry -> isExpiredAtOrBefore(entry.getValue().expireAt(), referenceTime));
        loginFailureStates.entrySet().removeIf(entry -> {
            LocalDateTime lockedUntil = entry.getValue().lockedUntil();
            return lockedUntil != null && !lockedUntil.isAfter(referenceTime);
        });
    }

    /**
     * 执行 C-10 清理逻辑。
     * <p>
     * 默认按 30 分钟空闲窗口清理会话快照，对齐当前认证锁定与会话治理的最小运维口径。
     */
    public void cleanupTimedOutSessions() {
        cleanupTimedOutSessions(LocalDateTime.now(), 30);
    }

    /**
     * 执行 C-10 清理逻辑的可测重载。
     *
     * @param referenceTime 当前参考时间
     * @param idleTimeoutMinutes 空闲超时分钟数
     */
    public void cleanupTimedOutSessions(LocalDateTime referenceTime, long idleTimeoutMinutes) {
        sessionStates.entrySet().removeIf(entry -> !entry.getValue().lastAccessAt().plusMinutes(idleTimeoutMinutes).isAfter(referenceTime));
    }

    /** 以下方法仅供单元测试断言运行时状态是否被正确清理，不对业务层暴露复杂内部结构。 */
    public boolean hasVerificationCode(String email) {
        return verificationCodeStates.containsKey(email);
    }

    public boolean hasRefreshToken(String refreshToken) {
        return refreshTokenStates.containsKey(refreshToken);
    }

    public RefreshTokenState getRefreshTokenState(String refreshToken) {
        return refreshTokenStates.get(refreshToken);
    }

    public boolean hasLoginFailureState(String account) {
        return loginFailureStates.containsKey(account);
    }

    public boolean hasSession(String sessionId) {
        return sessionStates.containsKey(sessionId);
    }

    public SessionState getSessionState(String sessionId) {
        return sessionStates.get(sessionId);
    }

    /**
     * 统一定义“到期即失效”的时间边界。
     * <p>
     * 认证链路中的验证码、刷新令牌快照都不应在 `referenceTime == expireAt` 的瞬间继续被视为有效，
     * 否则会形成边界时刻多放行一次的 off-by-one 漏洞。
     */
    public boolean isExpiredAtOrBefore(LocalDateTime expireAt, LocalDateTime referenceTime) {
        return expireAt != null && !expireAt.isAfter(referenceTime);
    }

    /** 登录失败状态快照。 */
    public record LoginFailureState(int failureCount, LocalDateTime lockedUntil) {
    }

    /** 验证码状态快照。 */
    public record VerificationCodeState(String code, LocalDateTime expireAt) {
    }

    /** 刷新令牌状态快照。 */
    public record RefreshTokenState(String userId, LocalDateTime expireAt) {
    }

    /** 会话快照。 */
    public record SessionState(String userId, LocalDateTime createdAt, LocalDateTime lastAccessAt) {
    }
}
