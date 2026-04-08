package com.jhun.backend.service.support.reservation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * reservation-create internal seed 配置。
 * <p>
 * 该入口默认只用于本机浏览器联调，因此配置默认值必须偏保守：
 * 一是仅允许回环地址访问，避免开关误开时把造数能力暴露给外部网络；
 * 二是默认不回传管理员账号明文密码，防止测试支撑接口顺带变成凭据分发入口。
 */
@Component
@ConfigurationProperties(prefix = "internal.seed.reservation-create")
public class ReservationCreateSeedProperties {

    /** internal seed 共享令牌请求头。 */
    public static final String ACCESS_TOKEN_HEADER = "X-Internal-Seed-Token";

    /**
     * 是否仅允许本机访问。
     * <p>
     * 默认开启，只有在明确知道当前环境已经通过其他手段封住外网访问面时，
     * 才允许显式关闭该保护。
     */
    private boolean loopbackOnly = true;

    /**
     * 是否回传管理员账号明文密码。
     * <p>
     * 默认关闭；普通用户账号仍可按需回传一次性联调密码，
     * 但管理员密码不应通过内部 seed 接口直接发放给调用方。
     */
    private boolean exposeAdminPasswords = false;

    /**
     * internal seed 访问令牌。
     * <p>
     * 该值只应通过外部环境变量或环境专属配置注入；
     * 一旦入口被启用而令牌为空，控制器会拒绝启动，避免再次出现“匿名可调”的安全回退。
     */
    private String accessToken;

    public boolean isLoopbackOnly() {
        return loopbackOnly;
    }

    public void setLoopbackOnly(boolean loopbackOnly) {
        this.loopbackOnly = loopbackOnly;
    }

    public boolean isExposeAdminPasswords() {
        return exposeAdminPasswords;
    }

    public void setExposeAdminPasswords(boolean exposeAdminPasswords) {
        this.exposeAdminPasswords = exposeAdminPasswords;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
