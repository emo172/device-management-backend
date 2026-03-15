package com.jhun.backend.integration.role;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Permission;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.PermissionMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 角色权限集成测试。
 * <p>
 * 用于验证角色列表、权限树读取与权限更新接口的最小闭环，
 * 重点保护“只有系统管理员可以读取或修改角色权限配置”的边界。
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证系统管理员可以查看角色列表，为角色权限配置页提供基础数据。
     */
    @Test
    void shouldAllowSystemAdminToListRoles() throws Exception {
        User systemAdmin = createUser("role-admin", "role-admin@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(get("/api/admin/roles")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    /**
     * 验证设备管理员不能更新角色权限，防止越权修改授权配置。
     */
    @Test
    void shouldRejectDeviceAdminUpdatingRolePermissions() throws Exception {
        User deviceAdmin = createUser("device-admin", "device-admin@example.com", "DEVICE_ADMIN");
        Role role = roleMapper.findByName("USER");
        Permission permission = permissionMapper.selectList(null).stream().findFirst().orElseThrow();

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", role.getId())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(permission.getId())))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证系统管理员可以读取“模块 -> 权限节点”树，并回显目标角色当前已绑定的勾选状态。
     * <p>
     * 测试环境种子权限只覆盖 USER_AUTH 模块，因此这里额外插入一条未分配权限，
     * 目的是在同一次响应中同时验证 selected=true 与 selected=false 两种回显状态。
     */
    @Test
    void shouldAllowSystemAdminToReadRolePermissionTree() throws Exception {
        User systemAdmin = createUser("role-tree-sa", "role-tree-sa@example.com", "SYSTEM_ADMIN");
        Role role = roleMapper.findByName("SYSTEM_ADMIN");
        insertUnselectedPermission();

        String content = mockMvc.perform(get("/api/admin/roles/{id}/permissions/tree", role.getId())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(content).path("data");
        JsonNode userAuthModule = findModule(root, "USER_AUTH");

        assertSelected(userAuthModule.path("permissions"), "AUTH", true);
        assertSelected(userAuthModule.path("permissions"), "UPDATE", true);
        assertSelected(userAuthModule.path("permissions"), "DELETE", false);
    }

    /**
     * 验证设备管理员不能读取角色权限树，防止非系统管理员窥探和编辑后台授权配置。
     */
    @Test
    void shouldRejectDeviceAdminReadingRolePermissionTree() throws Exception {
        User deviceAdmin = createUser("role-tree-da", "role-tree-da@example.com", "DEVICE_ADMIN");
        Role role = roleMapper.findByName("USER");

        mockMvc.perform(get("/api/admin/roles/{id}/permissions/tree", role.getId())
                        .header("Authorization", bearer(deviceAdmin, "DEVICE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    /**
     * 按模块标识在权限树响应中定位节点。
     * <p>
     * 该辅助方法让测试可以围绕业务模块断言，而不是依赖返回数组顺序，降低排序调整带来的脆弱性。
     */
    private JsonNode findModule(JsonNode modules, String moduleCode) {
        for (JsonNode module : modules) {
            if (moduleCode.equals(module.path("module").asText())) {
                return module;
            }
        }
        throw new AssertionError("未找到模块: " + moduleCode);
    }

    /**
     * 校验模块内某个权限节点是否按预期回显 selected。
     * <p>
     * 角色授权树最关键的是“完整返回 + 正确回显”，因此这里显式校验 code 与 selected 的对应关系。
     */
    private void assertSelected(JsonNode permissions, String code, boolean expectedSelected) {
        for (JsonNode permission : permissions) {
            if (code.equals(permission.path("code").asText())) {
                if (permission.path("selected").asBoolean() != expectedSelected) {
                    throw new AssertionError("权限 " + code + " 的 selected 与预期不一致");
                }
                return;
            }
        }
        throw new AssertionError("未找到权限: " + code);
    }

    /**
     * 插入一条未分配给 SYSTEM_ADMIN 的权限。
     * <p>
     * 这样做是为了验证授权树不是只返回“已拥有权限”，而是会把同模块下未勾选节点也一并返回给前端。
     */
    private void insertUnselectedPermission() {
        jdbcTemplate.update(
                """
                        INSERT INTO permission (id, code, name, module, description)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                "perm-user-delete-tree",
                "DELETE",
                "删除用户与角色配置",
                "USER_AUTH",
                "删除或清理角色配置");
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
        user.setPhone("13800138001");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
