package com.jhun.backend.common.exception;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.reservation.MultiReservationConflictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * <p>
 * 统一把控制层抛出的异常转换为 {@link Result}，确保接口响应结构稳定；
 * 当前实现已分别收口业务异常、访问拒绝、参数校验失败、未匹配资源与未分类异常，
 * 让客户端按可预期的失败语义接收统一文案，同时避免内部错误细节直接暴露给调用方。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_INTERNAL_ERROR_MESSAGE = "服务器内部错误，请稍后重试";
    private static final String RESOURCE_NOT_FOUND_MESSAGE = "请求资源不存在";

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
     * 处理多设备预约冲突异常。
     * <p>
     * 该异常用于把“整单失败但前端仍需知道哪些设备阻塞了提交”的场景稳定映射为 409，
     * 避免控制层手工拼装错误响应而破坏统一异常出口。
     *
     * @param exception 多设备预约冲突异常
     * @return 携带 blockingDevices[] 的统一失败响应
     */
    @ExceptionHandler(MultiReservationConflictException.class)
    public ResponseEntity<Result<MultiReservationConflictResponse>> handleMultiReservationConflictException(
            MultiReservationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(exception.getMessage(), exception.getResponse()));
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
     * 处理未匹配到控制器或静态资源的请求。
     * <p>
     * internal seed 默认关闭时，请求会落到 Spring MVC 的“资源不存在”分支；
     * 这里必须显式保持 404，避免该类正常缺路由场景被兜底 500 误报成服务端故障。
     *
     * @param exception 未匹配路由或静态资源异常
     * @return 统一 404 响应
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFoundException(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.error(RESOURCE_NOT_FOUND_MESSAGE));
    }

    /**
     * 处理未分类异常，避免异常栈直接暴露给调用方。
     *
     * @param exception 未分类异常
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("接口发生未分类异常，已按统一 500 响应返回安全文案", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(GENERIC_INTERNAL_ERROR_MESSAGE));
    }
}
