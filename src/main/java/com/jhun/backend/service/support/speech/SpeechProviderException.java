package com.jhun.backend.service.support.speech;

/**
 * 语音 provider 通用异常。
 * <p>
 * 语音供应商异常需要先在 provider 边界内被归一化，
 * 再由服务层翻译成稳定的业务错误文案，避免把底层 SDK 或网络细节直接暴露给客户端。
 */
public class SpeechProviderException extends RuntimeException {

    public SpeechProviderException(String message) {
        super(message);
    }

    public SpeechProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
