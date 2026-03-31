package com.jhun.backend.integration.reservation.diagnostics;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * reservation 列表 schema 漂移本地诊断集成测试。
 * <p>
 * 该类只保留“Mapper 依赖列名与测试库真实结构不一致”时的本地复现抓手；
 * 因为用例会主动改写 `reservation` 表结构，所以默认禁用，不纳入稳定接口契约或主回归覆盖。
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("仅用于本地 schema 漂移诊断，不纳入稳定接口契约回归")
class ReservationSchemaDriftDiagnosticIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 本地把 `sign_status` 临时改名为影子列，验证 schema 漂移会让固定分页列表直接返回 500。
     * <p>
     * 该场景会主动破坏测试表结构，只适合开发时诊断 SQL 层故障，不应作为 CI 中的稳定 API 契约。
     */
    @Test
    void shouldReturnInternalServerErrorForSchemaDriftOnFixedPaging() throws Exception {
        User systemAdmin = createSystemAdmin("rsv-schema-admin", "reserve-schema-admin@example.com");

        try {
            jdbcTemplate.execute("ALTER TABLE reservation RENAME COLUMN sign_status TO sign_status_shadow");

            mockMvc.perform(get("/api/reservations")
                            .header("Authorization", bearer(systemAdmin))
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isInternalServerError());
        } finally {
            /*
             * 这个恢复动作必须保留在 finally：即使断言失败，也要把列名改回去，
             * 否则当前 JVM 后续的本地诊断或其他测试都会被污染。
             */
            jdbcTemplate.execute("ALTER TABLE reservation RENAME COLUMN sign_status_shadow TO sign_status");
        }
    }

    private User createSystemAdmin(String username, String email) {
        Role role = roleMapper.findByName("SYSTEM_ADMIN");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138444");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), "SYSTEM_ADMIN");
    }
}
