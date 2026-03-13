package com.jhun.backend.unit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.common.exception.GlobalExceptionHandler;
import com.jhun.backend.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 全局异常处理器单元测试。
 * <p>
 * 用于验证业务异常被统一包装为标准响应体，防止异常处理层回退到不一致的错误格式。
 */
class GlobalExceptionHandlerTest {

    /**
     * 验证业务异常消息会原样写入统一响应体，保护前端依赖的错误提示契约。
     */
    @Test
    void shouldWrapBusinessExceptionWithUnifiedBody() {
        ResponseEntity<Result<Void>> response = new GlobalExceptionHandler()
                .handleBusinessException(new BusinessException("user_not_found"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertEquals("user_not_found", body.getMessage());
    }
}
