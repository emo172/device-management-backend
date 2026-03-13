package com.jhun.backend.unit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 配置骨架测试。
 * <p>
 * 该测试锁定多环境配置是否能在 test profile 下正确加载，避免后续调整配置文件时破坏 JWT、应用名等关键基础参数。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationProfileConfigTest {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${jwt.access-token-validity}")
    private Long accessTokenValidity;

    /**
     * 验证测试环境能够读取基础应用名与 JWT 有效期配置。
     */
    @Test
    void shouldLoadTestProfileConfiguration() {
        assertEquals("device-management-backend", applicationName);
        assertEquals(86400L, accessTokenValidity);
    }
}
