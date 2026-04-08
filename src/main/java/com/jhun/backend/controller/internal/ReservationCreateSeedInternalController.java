package com.jhun.backend.controller.internal;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.reservation.ReservationCreateSeedRequest;
import com.jhun.backend.dto.reservation.ReservationCreateSeedResponse;
import com.jhun.backend.service.support.reservation.ReservationCreateSeedProperties;
import com.jhun.backend.service.support.reservation.ReservationCreateSeedSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * reservation-create 浏览器验证 internal seed 控制器。
 * <p>
 * 这是一个刻意收敛的 test/dev 内部入口：
 * 只服务后续 `scripts/e2e/seed-reservation-create.mjs` 调真实后端准备场景真相，
 * 默认生产不会注册，且只接受固定场景名，避免演化成对外可用的通用造数 API。
 */
@RestController
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "internal.seed.reservation-create", name = "enabled", havingValue = "true")
@RequestMapping("/api/internal/seeds/reservation-create")
public class ReservationCreateSeedInternalController {

    private static final String LOOPBACK_ONLY_DENIED_MESSAGE = "internal seed 仅允许本机访问";
    private static final String INVALID_ACCESS_TOKEN_MESSAGE = "internal seed 访问令牌无效";

    private final ReservationCreateSeedSupport reservationCreateSeedSupport;
    private final ReservationCreateSeedProperties reservationCreateSeedProperties;

    public ReservationCreateSeedInternalController(
            ReservationCreateSeedSupport reservationCreateSeedSupport,
            ReservationCreateSeedProperties reservationCreateSeedProperties) {
        this.reservationCreateSeedSupport = reservationCreateSeedSupport;
        this.reservationCreateSeedProperties = reservationCreateSeedProperties;
        validateAccessTokenConfiguration();
    }

    /**
     * 生成 reservation-create 浏览器验证所需的最小真实数据。
     * <p>
     * 这里不要求登录态，因为入口自身已经被 profile + 显式开关双重保护；
     * 但默认仍额外要求“共享令牌 + 本机回环地址”双重保护，避免共享 dev/test 环境把该造数能力直接暴露给外部网络。
     */
    @PostMapping
    public Result<ReservationCreateSeedResponse> seed(
            @RequestBody ReservationCreateSeedRequest request,
            HttpServletRequest servletRequest) {
        ensureAccessTokenMatches(servletRequest);
        ensureLoopbackOnlyIfRequired(servletRequest);
        return Result.success(reservationCreateSeedSupport.seed(request));
    }

    /**
     * internal seed 一旦启用就必须带访问令牌。
     * <p>
     * 该控制器本身只在显式打开开关时注册，因此这里直接在构造期 fail-fast，
     * 避免“忘了配 token 就退回到匿名可调”的危险默认值。
     */
    private void validateAccessTokenConfiguration() {
        String accessToken = reservationCreateSeedProperties.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("启用 reservation-create internal seed 时必须配置 internal.seed.reservation-create.access-token");
        }
    }

    /**
     * 校验 internal seed 访问令牌。
     * <p>
     * 共享令牌用于抵御“请求经由同机代理转发后 remoteAddr 仍显示 loopback”的场景，
     * 因此它与回环地址校验属于互补关系，而不是二选一。
     */
    private void ensureAccessTokenMatches(HttpServletRequest servletRequest) {
        String requestAccessToken = servletRequest.getHeader(ReservationCreateSeedProperties.ACCESS_TOKEN_HEADER);
        String configuredAccessToken = reservationCreateSeedProperties.getAccessToken();
        if (requestAccessToken == null || !requestAccessToken.equals(configuredAccessToken)) {
            throw new AccessDeniedException(INVALID_ACCESS_TOKEN_MESSAGE);
        }
    }

    /**
     * 在默认配置下仅允许本机访问 internal seed。
     * <p>
     * 该控制器仍保持匿名，是为了让浏览器联调脚本能够从 0 自举普通用户场景；
     * 但匿名不等于公网可访问，因此必须额外收紧到 loopback，降低误开配置时的暴露面。
     */
    private void ensureLoopbackOnlyIfRequired(HttpServletRequest servletRequest) {
        if (!reservationCreateSeedProperties.isLoopbackOnly()) {
            return;
        }
        String remoteAddress = servletRequest.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            throw new AccessDeniedException(LOOPBACK_ONLY_DENIED_MESSAGE);
        }
        try {
            if (!InetAddress.getByName(remoteAddress).isLoopbackAddress()) {
                throw new AccessDeniedException(LOOPBACK_ONLY_DENIED_MESSAGE);
            }
        } catch (UnknownHostException exception) {
            throw new AccessDeniedException(LOOPBACK_ONLY_DENIED_MESSAGE);
        }
    }
}
