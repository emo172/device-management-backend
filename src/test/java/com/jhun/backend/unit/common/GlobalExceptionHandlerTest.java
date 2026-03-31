package com.jhun.backend.unit.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    /**
     * 验证业务异常消息会原样写入统一响应体，保护前端依赖的错误提示契约。
     */
    @Test
    void shouldWrapBusinessExceptionWithUnifiedBody() {
        ResponseEntity<Result<Void>> response = globalExceptionHandler
                .handleBusinessException(new BusinessException("user_not_found"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("user_not_found", body.getMessage());
    }

    /**
     * 模拟底层运行时异常把内部实现细节带到异常消息中的场景，验证兜底 500 只返回统一安全文案。
     * 该用例用于防止后续维护时把 SQL、堆栈或其他服务端细节重新透传给前端。
     */
    @Test
    void shouldSanitizeGenericInternalServerErrorMessage() {
        ResponseEntity<Result<Void>> response = globalExceptionHandler
                .handleException(new RuntimeException("模拟数据库 SQL 细节"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals("服务器内部错误，请稍后重试", body.getMessage());
    }
}
