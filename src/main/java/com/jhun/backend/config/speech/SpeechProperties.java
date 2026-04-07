package com.jhun.backend.config.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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
