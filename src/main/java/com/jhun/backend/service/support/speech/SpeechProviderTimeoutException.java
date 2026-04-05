package com.jhun.backend.service.support.speech;

/**
 * 语音 provider 超时异常。
 * <p>
 * 单独拆出超时异常是为了让服务层返回更明确的“稍后重试”语义，
 * 避免把网络超时和一般供应商失败都折叠成同一条模糊错误信息。
 */
public class SpeechProviderTimeoutException extends SpeechProviderException {

    public SpeechProviderTimeoutException(String message) {
        super(message);
    }

    public SpeechProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
