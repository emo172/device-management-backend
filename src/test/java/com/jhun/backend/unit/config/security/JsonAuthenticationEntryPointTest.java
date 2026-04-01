package com.jhun.backend.unit.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JsonAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 统一 401 认证入口单元测试。
 * <p>
 * 该测试直接锁定安全层统一未认证出口的响应结构，避免后续把匿名请求、过期令牌和非法令牌
 * 收口到同一 `AuthenticationEntryPoint` 时，又回退成裸状态码或散装 JSON。
 */
class JsonAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint = new JsonAuthenticationEntryPoint();

    /**
     * 验证统一认证入口会返回 `401 + Result.error(...)` 结构，保护安全层对前端的统一失败契约。
     */
    @Test
    void shouldWriteUnifiedUnauthorizedResultBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jsonAuthenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("anonymous request"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals(1, body.path("code").asInt());
        assertEquals("未登录或登录已过期，请重新登录", body.path("message").asText());
        assertTrue(body.path("data").isNull());
    }
}
