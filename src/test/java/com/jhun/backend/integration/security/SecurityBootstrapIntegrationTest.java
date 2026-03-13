package com.jhun.backend.integration.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 安全基线集成测试。
 * <p>
 * 通过真实 WebApplicationContext 构建 MockMvc，验证“除白名单外默认需要鉴权”的阶段 0 安全规则已经生效。
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityBootstrapIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    /**
     * 在每个测试前重建带安全过滤器链的 MockMvc，确保断言覆盖真实鉴权行为。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证匿名用户访问受保护后台接口时会被拒绝，防止安全配置意外放开管理接口。
     */
    @Test
    void shouldRejectAnonymousProtectedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
