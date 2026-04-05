package com.jhun.backend.config.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音配置属性。
 * <p>
 * 当前阶段显式暴露 `speech.enabled`、provider、上传大小限制与 Azure Speech 凭据占位。
 * 中文 locale、浏览器录音格式和 TTS 输出格式继续由语音契约常量统一固化，避免运行时配置改写联调口径。
 */
@ConfigurationProperties(prefix = "speech")
public class SpeechProperties {

    private boolean enabled = false;

    private String provider = "azure";

    private AzureProperties azure = new AzureProperties();

    /**
     * 浏览器录音上传大小上限。
     * <p>
     * 这里默认固定为 10MB，与 task 2 的接口契约保持一致；
     * 服务层仍会基于该值做显式校验，避免仅依赖容器默认 multipart 行为导致错误语义不稳定。
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

    public AzureProperties getAzure() {
        return azure;
    }

    public void setAzure(AzureProperties azure) {
        this.azure = azure;
    }

    public long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }

    public void setMaxUploadSizeBytes(long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    /**
     * Azure Speech 凭据占位。
     * <p>
     * 当前 provider 基线实现仍通过测试替身和 feature flag 控制行为，但文档与部署配置需要有真实可绑定的键，
     * 避免把不存在的环境变量名写进仓库说明。
     */
    public static class AzureProperties {

        private String region = "";

        private String key = "";

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
