package com.jhun.backend.integration.user;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 用户管理集成测试。
 * <p>
 * 用于验证系统管理员具备用户列表、用户详情、状态调整与冻结管理能力，
 * 同时防止普通用户或设备管理员越权访问后台用户管理接口。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserControllerIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证系统管理员可以禁用普通用户，保护后台用户状态管理能力。
     */
    @Test
    void shouldAllowSystemAdminToDisableUser() throws Exception {
        User systemAdmin = createUser("sysadmin", "sysadmin@example.com", "SYSTEM_ADMIN");
        User normalUser = createUser("normal-user", "normal@example.com", "USER");
        String systemAdminToken = bearer(systemAdmin, "SYSTEM_ADMIN");

        mockMvc.perform(put("/api/admin/users/{id}/status", normalUser.getId())
                        .header("Authorization", systemAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": 0,
                                  "reason": "违规使用设备"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    /**
     * 验证系统管理员可以冻结普通用户，保护冻结状态规则的管理入口。
     */
    @Test
    void shouldAllowSystemAdminToFreezeUser() throws Exception {
        User systemAdmin = createUser("sysadmin2", "sysadmin2@example.com", "SYSTEM_ADMIN");
        User normalUser = createUser("normal-user2", "normal2@example.com", "USER");

        mockMvc.perform(post("/api/admin/users/{id}/freeze", normalUser.getId())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "freezeStatus": "FROZEN",
                                  "reason": "多次逾期未归还"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freezeStatus").value("FROZEN"));
    }

    /**
     * 验证系统管理员可以查询用户列表，确保后台用户页具备基础分页数据源。
     */
    @Test
    void shouldAllowSystemAdminToListUsers() throws Exception {
        User systemAdmin = createUser("sysadmin-list", "sysadmin-list@example.com", "SYSTEM_ADMIN");
        User normalUser = createUser("normal-list-user", "normal-list-user@example.com", "USER");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.records[*].username").value(hasItem("normal-list-user")));
    }

    /**
     * 验证系统管理员可以查询用户详情，确保后台详情页可以展示角色与冻结状态等核心字段。
     */
    @Test
    void shouldAllowSystemAdminToGetUserDetail() throws Exception {
        User systemAdmin = createUser("sysadmin-detail", "sysadmin-detail@example.com", "SYSTEM_ADMIN");
        User normalUser = createUser("normal-detail-user", "normal-detail-user@example.com", "USER");

        mockMvc.perform(get("/api/admin/users/{id}", normalUser.getId())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(normalUser.getId()))
                .andExpect(jsonPath("$.data.roleName").value("USER"));
    }

    /**
     * 验证非系统管理员无法访问用户列表，防止设备管理员或普通用户越权浏览全量账号信息。
     */
    @Test
    void shouldRejectListUsersForNonSystemAdmin() throws Exception {
        User normalUser = createUser("normal-no-access", "normal-no-access@example.com", "USER");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(normalUser, "USER"))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证非系统管理员不能访问用户详情，防止详情接口成为绕过用户列表权限的越权入口。
     */
    @Test
    void shouldRejectGetUserDetailForNonSystemAdmin() throws Exception {
        User normalUser = createUser("no-detail-user", "normal-no-detail-access@example.com", "USER");
        User targetUser = createUser("detail-target", "target-no-detail-access@example.com", "USER");

        mockMvc.perform(get("/api/admin/users/{id}", targetUser.getId())
                        .header("Authorization", bearer(normalUser, "USER")))
                .andExpect(status().isForbidden());
    }

    private User createUser(String username, String email, String roleName) {
        Role role = roleMapper.findByName(roleName);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138000");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
