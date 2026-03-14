package com.jhun.backend.integration.category;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 设备分类控制器集成测试。
 * <p>
 * 用于验证分类树查询与同层分类名称唯一性，避免分类页出现重复节点或层级结构错误。
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
        String token = bearer(createAdminUser("category-admin", "category-admin@example.com"));
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

        mockMvc.perform(get("/api/device-categories/tree")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value(rootCategoryName))
                .andExpect(jsonPath("$.data[0].children[0].name").value(childCategoryName));
    }

    /**
     * 验证同一父分类下不能创建同名分类，保护 SQL 中 parent_id + name 的唯一性约束。
     */
    @Test
    void shouldRejectDuplicateCategoryNameUnderSameParent() throws Exception {
        String token = bearer(createAdminUser("category-admin-2", "category-admin-2@example.com"));
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

    private User createAdminUser(String username, String email) {
        Role role = roleMapper.findByName("SYSTEM_ADMIN");
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

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), "SYSTEM_ADMIN");
    }
}
