package com.jhun.backend.common.exception;

import com.jhun.backend.common.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
     * 处理鉴权成功但授权失败的场景，避免权限异常被兜底成 500。
     *
     * @param exception 访问拒绝异常
     * @return 统一失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(exception.getMessage()));
    }

    /**
     * 处理请求体参数校验失败。
     * <p>
     * 控制层启用 `@Valid` 后，请求字段越界或为空应直接返回 400，避免无效输入继续进入服务层或数据库层。
     *
     * @param exception 方法参数校验异常
     * @return 统一失败响应，消息优先返回首个字段校验错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage() == null ? "请求参数校验失败" : fieldError.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(message));
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
