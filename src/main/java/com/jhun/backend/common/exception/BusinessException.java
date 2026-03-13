package com.jhun.backend.common.exception;

/**
 * 业务异常。
 * <p>
 * 用于承载可预期的领域错误，例如资源不存在、状态非法流转或权限不足等场景，
 * 由统一异常处理器转换为标准响应，避免业务层直接拼装 HTTP 返回体。
 */
public class BusinessException extends RuntimeException {

    /**
     * 使用业务可读消息构造异常。
     *
     * @param message 业务错误消息
     */
    public BusinessException(String message) {
        super(message);
    }
}
