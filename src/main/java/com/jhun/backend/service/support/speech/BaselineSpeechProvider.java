package com.jhun.backend.service.support.speech;

import com.jhun.backend.config.speech.SpeechProperties;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Azure 语音 provider 基线实现。
 * <p>
 * 当前实现已经接入 Azure Speech 官方 SDK，但仍保留 `Baseline` 命名作为现有 Bean 与测试引用的稳定挂点。
 * provider 自身只负责三件事：
 * 1) 保证当前路径仍然是 Azure-only；
 * 2) 在真正调用 SDK 前校验 `speech.azure.*` 必需配置；
 * 3) 把 Azure helper 抛出的超时 / 普通失败统一收口为既有 provider 异常模型，供服务层继续翻译业务文案。
 */
@Component
public class BaselineSpeechProvider implements SpeechProvider {

    private final SpeechProperties speechProperties;

    private final AzureSpeechSdkClient azureSpeechSdkClient;

    public BaselineSpeechProvider(SpeechProperties speechProperties, AzureSpeechSdkClient azureSpeechSdkClient) {
        this.speechProperties = speechProperties;
        this.azureSpeechSdkClient = azureSpeechSdkClient;
    }

    @Override
    public String providerName() {
        validateAzureProviderSelected();
        return SpeechContract.PROVIDER_AZURE;
    }

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        validateAzureProviderSelected();
        validateAzureCredentialsConfigured();
        try {
            return azureSpeechSdkClient.transcribe(speechProperties.getAzure(), request, SpeechContract.PROVIDER_AZURE);
        } catch (AzureSpeechSdkClient.TimeoutException exception) {
            throw new SpeechProviderTimeoutException(exception.getMessage(), exception);
        } catch (AzureSpeechSdkClient.FailureException exception) {
            throw new SpeechProviderException(exception.getMessage(), exception);
        }
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        validateAzureProviderSelected();
        validateAzureCredentialsConfigured();
        try {
            return azureSpeechSdkClient.synthesize(speechProperties.getAzure(), request, SpeechContract.PROVIDER_AZURE);
        } catch (AzureSpeechSdkClient.TimeoutException exception) {
            throw new SpeechProviderTimeoutException(exception.getMessage(), exception);
        } catch (AzureSpeechSdkClient.FailureException exception) {
            throw new SpeechProviderException(exception.getMessage(), exception);
        }
    }

    /**
     * 当前基线只准备 Azure 语音 provider 路径。
     * <p>
     * 若配置被误改成其他 provider，这里直接 fail-fast，避免调用方误以为系统已经支持多供应商切换。
     */
    private void validateAzureProviderSelected() {
        if (!SpeechContract.PROVIDER_AZURE.equalsIgnoreCase(speechProperties.getProvider())) {
            throw new SpeechProviderException("当前仅支持 Azure Speech provider");
        }
    }

    /**
     * Azure 凭据只在真正触发语音请求时校验，避免测试上下文或 feature flag 关闭场景被无关地阻塞在 Bean 初始化阶段。
     */
    private void validateAzureCredentialsConfigured() {
        SpeechProperties.AzureProperties azureProperties = speechProperties.getAzure();
        if (azureProperties == null
                || !StringUtils.hasText(azureProperties.getRegion())
                || !StringUtils.hasText(azureProperties.getKey())) {
            throw new SpeechProviderException("Azure Speech 配置不完整，请检查 speech.azure.region / speech.azure.key");
        }
    }
}
