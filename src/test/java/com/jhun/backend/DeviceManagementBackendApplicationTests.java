package com.jhun.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 启动类基础集成测试。
 * <p>
 * 用于验证最小应用上下文在测试配置下可以成功启动，防止基础依赖、配置装配或安全链调整导致整个后端无法启动。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeviceManagementBackendApplicationTests {

    /**
     * 验证 Spring Boot 测试上下文可以正常加载。
     */
    @Test
    void contextLoads() {
    }

}
