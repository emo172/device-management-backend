package com.jhun.backend.config.speech;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "speech")
public class SpeechProperties {

    private boolean enabled = false;

    private String provider = "iflytek";

    private IflytekProperties iflytek = new IflytekProperties();

    /**
     * 浏览器录音上传大小上限。
     * <p>
     * 正式公开合同已冻结为 `audio/wav`（16k / 16bit / 单声道 PCM）且最长 60 秒，理论大小远小于 10MB；
     * 这里继续保留 10MB 作为服务层兜底上限，确保超限时仍能返回稳定业务错误，而不是落回容器默认异常。
     */
    private long maxUploadSizeBytes = 10L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断当前运行时是否真的具备可对外暴露的语音转写能力。
     * <p>
     * `/api/ai/capabilities` 面向前端暴露的是“用户现在点开录音入口后能否真正转写成功”的最小事实，
     * 因此这里不能只看 `speech.enabled`，还必须同时校验 provider 是否落在当前唯一支持的 Iflytek 路径，
     * 以及一期必需的 `app-id / api-key / api-secret` 是否已经配置齐全。
     */
    public boolean isTranscriptionAvailable() {
        return enabled && isIflytekProviderSelected() && hasIflytekCredentialsConfigured();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 判断当前 provider 选择是否应落到一期默认的讯飞实现。
     * <p>
     * 这里继续把空白 provider 归一化为默认值 `iflytek`，并让 capabilities 暴露、Bean 装配与运行时 provider 校验共同复用，
     * 因而这里必须成为语音 provider 选择的唯一真相源，避免不同层各自解释空白字符串而出现语义漂移。
     */
    public boolean isIflytekProviderSelected() {
        return "iflytek".equals(resolveProviderName());
    }

    public IflytekProperties getIflytek() {
        return iflytek;
    }

    public void setIflytek(IflytekProperties iflytek) {
        this.iflytek = iflytek;
    }

    public long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }

    public void setMaxUploadSizeBytes(long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    /**
     * 判断一期讯飞转写所需的鉴权配置是否齐全。
     * <p>
     * 当前仓库虽然允许 `speech.enabled=false` 时缺省凭据启动，但一旦要把语音入口暴露给前端，
     * 就必须保证三项正式鉴权参数已经具备，避免接口声称“可用”却在第一次真实调用时才暴露配置缺失。
     */
    public boolean hasIflytekCredentialsConfigured() {
        return iflytek != null
                && StringUtils.hasText(iflytek.getAppId())
                && StringUtils.hasText(iflytek.getApiKey())
                && StringUtils.hasText(iflytek.getApiSecret());
    }

    String resolveProviderName() {
        if (!StringUtils.hasText(provider)) {
            return "iflytek";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    public static class IflytekProperties {

        private String appId = "";

        private String apiKey = "";

        private String apiSecret = "";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }
    }
}
