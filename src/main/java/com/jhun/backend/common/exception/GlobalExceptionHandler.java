package com.jhun.backend.common.exception;

import com.jhun.backend.common.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 统一把控制层抛出的异常转换为 {@link Result}，确保接口响应结构稳定；
 * 当前阶段先覆盖业务异常与兜底异常，后续可按模块细分校验异常、鉴权异常等处理逻辑。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常并保留原始业务消息。
     *
     * @param exception 业务异常
     * @return 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(exception.getMessage()));
    }

    /**
     * 处理未分类异常，避免异常栈直接暴露给调用方。
     *
     * @param exception 未分类异常
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(exception.getMessage()));
    }
}
