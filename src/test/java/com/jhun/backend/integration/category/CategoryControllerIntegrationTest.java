package com.jhun.backend.integration.category;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 设备分类控制器集成测试。
 * <p>
 * 用于验证分类树查询、同层分类名称唯一性与分类写权限边界，
 * 避免分类页出现重复节点、层级结构错误或越权创建分类。
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryControllerIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证分类树接口可以返回父子节点结构，保护分类页树形展示契约。
     */
    @Test
    void shouldReturnCategoryTree() throws Exception {
        String token = bearer(createDeviceAdminUser("category-admin", "category-admin@example.com"), "DEVICE_ADMIN");
        String rootCategoryName = "实验设备-树";
        String childCategoryName = "显微镜-树";

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "一级分类",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(rootCategoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "parentName": "%s",
                                  "description": "二级分类",
                                  "sortOrder": 2,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(childCategoryName, rootCategoryName)))
                .andExpect(status().isOk());

        /*
         * 分类树接口返回的是整张根分类集合，根节点顺序由 sort_order / created_at 决定，
         * 不能把“我刚创建的根分类刚好在第一个位置”当成接口契约。
         * 因此这里按名称定位目标根节点，避免跨测试残留分类把断言误伤成顺序问题。
         */
        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/device-categories/tree")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");

        JsonNode targetRoot = findCategoryNodeByName(tree, rootCategoryName);
        org.junit.jupiter.api.Assertions.assertNotNull(targetRoot);
        org.junit.jupiter.api.Assertions.assertEquals(childCategoryName, targetRoot.path("children").get(0).path("name").asText());
    }

    /**
     * 验证同一父分类下不能创建同名分类，保护 SQL 中 parent_id + name 的唯一性约束。
     */
    @Test
    void shouldRejectDuplicateCategoryNameUnderSameParent() throws Exception {
        String token = bearer(createDeviceAdminUser("category-admin-2", "category-admin-2@example.com"), "DEVICE_ADMIN");
        String rootCategoryName = "办公设备-唯一";
        String childCategoryName = "打印机-唯一";

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "一级分类",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(rootCategoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "parentName": "%s",
                                  "description": "二级分类A",
                                  "sortOrder": 2,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(childCategoryName, rootCategoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "parentName": "%s",
                                  "description": "二级分类B",
                                  "sortOrder": 3,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(childCategoryName, rootCategoryName)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证子分类名称不能再被当作 `parentName` 继续创建下一级分类，
     * 以锁定后端当前“只按根分类名解析父级”的真实契约边界。
     */
    @Test
    void shouldRejectCreateCategoryWhenParentNamePointsToChildCategory() throws Exception {
        String token = bearer(createDeviceAdminUser("category-admin-3", "category-admin-3@example.com"), "DEVICE_ADMIN");
        String rootCategoryName = "影像设备-根";
        String childCategoryName = "摄像机-子";

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "一级分类",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(rootCategoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "parentName": "%s",
                                  "description": "二级分类",
                                  "sortOrder": 2,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(childCategoryName, rootCategoryName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "镜头模组-非法三级",
                                  "parentName": "%s",
                                  "description": "不应支持子分类名称作为父级",
                                  "sortOrder": 3,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """.formatted(childCategoryName)))
                .andExpect(status().isBadRequest());

        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/device-categories/tree")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .path("data");

        /*
         * 该测试类不会在每个用例间自动清空分类表，
         * 因此这里按名称定位刚创建的根节点，避免把“列表排序位置变化”误判成父级解析契约失效。
         */
        JsonNode targetRoot = findCategoryNodeByName(tree, rootCategoryName);
        org.junit.jupiter.api.Assertions.assertNotNull(targetRoot);
        org.junit.jupiter.api.Assertions.assertEquals(childCategoryName, targetRoot.path("children").get(0).path("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, targetRoot.path("children").get(0).path("children").size());
    }

    /**
     * 验证普通用户不能创建分类，保护“分类写入口只属于 DEVICE_ADMIN”的职责边界。
     */
    @Test
    void shouldRejectCreateCategoryByNormalUser() throws Exception {
        String token = bearer(createNormalUser("category-user-1", "category-user-1@example.com"), "USER");

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "越权分类",
                                  "description": "普通用户不应创建分类",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证系统管理员同样不能创建分类，保护“设备分类写权限只属于 DEVICE_ADMIN”的职责边界。
     */
    @Test
    void shouldRejectCreateCategoryBySystemAdmin() throws Exception {
        String token = bearer(createSystemAdminUser("cat-sys-admin-1", "category-system-admin-1@example.com"), "SYSTEM_ADMIN");

        mockMvc.perform(post("/api/device-categories")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "系统管理员越权分类",
                                  "description": "系统管理员不应创建分类",
                                  "sortOrder": 1,
                                  "defaultApprovalMode": "DEVICE_ONLY"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private User createDeviceAdminUser(String username, String email) {
        Role role = roleMapper.findByName("DEVICE_ADMIN");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138222");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private User createNormalUser(String username, String email) {
        Role role = roleMapper.findByName("USER");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138223");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private User createSystemAdminUser(String username, String email) {
        Role role = roleMapper.findByName("SYSTEM_ADMIN");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138224");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    private JsonNode findCategoryNodeByName(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }
}
