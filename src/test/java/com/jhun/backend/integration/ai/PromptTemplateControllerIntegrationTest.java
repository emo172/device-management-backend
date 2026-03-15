package com.jhun.backend.integration.ai;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.common.enums.PromptTemplateType;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Prompt 模板控制器集成测试。
 * <p>
 * 该测试验证系统管理员可以管理 Prompt 模板，而普通用户不能越权访问后台模板接口。
 */
@SpringBootTest
@ActiveProfiles("test")
class PromptTemplateControllerIntegrationTest {

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
     * 验证系统管理员可以创建并查询 Prompt 模板，保护后台模板配置最小闭环。
     */
    @Test
    void shouldAllowSystemAdminToCreateAndListPromptTemplates() throws Exception {
        User systemAdmin = createUser("prompt-admin", "prompt-admin@example.com", "SYSTEM_ADMIN");
        String createRequestBody = """
                {
                  "name": "结果反馈模板-新增",
                  "code": "RESULT_FEEDBACK_CUSTOM",
                  "content": "请给用户返回结果：{message}",
                  "type": "%s",
                  "description": "测试新增模板",
                  "variables": "[\\\"message\\\"]",
                  "active": false,
                  "version": "1.1"
                }
                """.formatted(PromptTemplateType.RESULT_FEEDBACK.name());

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("RESULT_FEEDBACK_CUSTOM"))
                .andExpect(jsonPath("$.data.type").value(PromptTemplateType.RESULT_FEEDBACK.name()));

        mockMvc.perform(get("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='RESULT_FEEDBACK_CUSTOM')]").exists());
    }

    /**
     * 验证系统管理员可以更新模板开关与内容，保护 Prompt 运维能力不会退化为只读。
     */
    @Test
    void shouldAllowSystemAdminToUpdatePromptTemplate() throws Exception {
        User systemAdmin = createUser("prompt-admin-update", "prompt-admin-update@example.com", "SYSTEM_ADMIN");
        String templateId = insertPromptTemplate();
        String updateRequestBody = """
                {
                  "name": "意图识别模板-已更新",
                  "code": "INTENT_PROMPT_FOR_UPDATE",
                  "content": "更新后的模板内容",
                  "type": "%s",
                  "description": "测试更新模板",
                  "variables": "[\\\"message\\\"]",
                  "active": false,
                  "version": "2.0"
                }
                """.formatted(PromptTemplateType.INTENT_RECOGNITION.name());

        mockMvc.perform(put("/api/ai/prompts/{id}", templateId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("意图识别模板-已更新"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    /**
     * 验证模板创建前会先做字段归一化，再进入类型和唯一性校验，避免尾随空白绕过业务规则。
     */
    @Test
    void shouldNormalizePromptTemplateFieldsBeforeValidationAndSaving() throws Exception {
        User systemAdmin = createUser("prompt-admin-norm", "prompt-admin-normalize@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "归一化模板   ",
                                  "code": "NORMALIZED_PROMPT   ",
                                  "content": "  归一化内容  ",
                                  "type": "RESULT_FEEDBACK   ",
                                  "description": "测试归一化",
                                  "variables": "[\\\"message\\\"]",
                                  "active": false,
                                  "version": "1.0   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("归一化模板"))
                .andExpect(jsonPath("$.data.code").value("NORMALIZED_PROMPT"))
                .andExpect(jsonPath("$.data.type").value(PromptTemplateType.RESULT_FEEDBACK.name()))
                .andExpect(jsonPath("$.data.version").value("1.0"));

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "另一个模板",
                                  "code": "NORMALIZED_PROMPT   ",
                                  "content": "重复代码测试",
                                  "type": "RESULT_FEEDBACK",
                                  "description": "测试唯一性",
                                  "variables": "[\\\"message\\\"]",
                                  "active": false,
                                  "version": "1.1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prompt 模板代码已存在"));
    }

    /**
     * 验证同一模板类型同时只能保留一条启用模板，防止运行时靠排序偷偷挑一条生效。
     */
    @Test
    void shouldRejectSecondActivePromptTemplateOfSameType() throws Exception {
        User systemAdmin = createUser("prompt-admin-act", "prompt-admin-active@example.com", "SYSTEM_ADMIN");
        insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK);

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "第二条启用结果反馈模板",
                                  "code": "RESULT_FEEDBACK_ACTIVE_2",
                                  "content": "第二条启用模板",
                                  "type": "RESULT_FEEDBACK",
                                  "description": "测试同类型启用冲突",
                                  "variables": "[\\\"message\\\"]",
                                  "active": true,
                                  "version": "1.1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("同一 Prompt 模板类型只能有一条启用模板"));
    }

    /**
     * 验证超出 DDL 长度上限的字段会在接口层直接被 400 拦截，避免非法数据进入服务层。
     */
    @Test
    void shouldRejectOverlongPromptTemplateFieldAtControllerLayer() throws Exception {
        User systemAdmin = createUser("prompt-admin-len", "prompt-admin-too-long@example.com", "SYSTEM_ADMIN");
        String overlongName = "N".repeat(101);

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "code": "RESULT_FEEDBACK_LONG_NAME",
                                  "content": "超长名称测试",
                                  "type": "RESULT_FEEDBACK",
                                  "description": "测试接口层校验",
                                  "variables": "[\\\"message\\\"]",
                                  "active": false,
                                  "version": "1.0"
                                }
                                """.formatted(overlongName)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证模板类型不在固定枚举口径内时，会在接口层直接返回 400。
     */
    @Test
    void shouldRejectInvalidPromptTemplateTypeAtControllerLayer() throws Exception {
        User systemAdmin = createUser("prompt-admin-type", "prompt-admin-type@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "非法类型模板",
                                  "code": "INVALID_TYPE_PROMPT",
                                  "content": "非法类型测试",
                                  "type": "result_feedback",
                                  "description": "测试 DTO 类型校验",
                                  "variables": "[\\\"message\\\"]",
                                  "active": false,
                                  "version": "1.0"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证版本号不符合约定格式时，会在接口层直接返回 400。
     */
    @Test
    void shouldRejectInvalidPromptTemplateVersionAtControllerLayer() throws Exception {
        User systemAdmin = createUser("prompt-admin-ver", "prompt-admin-ver@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(post("/api/ai/prompts")
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "非法版本模板",
                                  "code": "INVALID_VERSION_PROMPT",
                                  "content": "非法版本测试",
                                  "type": "RESULT_FEEDBACK",
                                  "description": "测试 DTO 版本校验",
                                  "variables": "[\\\"message\\\"]",
                                  "active": false,
                                  "version": "version-one"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证普通用户无法访问 Prompt 模板管理接口，避免 AI 使用入口与后台管理入口权限串线。
     */
    @Test
    void shouldRejectUserManagingPromptTemplates() throws Exception {
        User user = createUser("prompt-user", "prompt-user@example.com", "USER");

        mockMvc.perform(get("/api/ai/prompts")
                        .header("Authorization", bearer(user, "USER")))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证系统管理员可以删除已停用模板，避免后台模板库积累无法清理的历史废弃模板。
     */
    @Test
    void shouldAllowSystemAdminToDeleteInactivePromptTemplate() throws Exception {
        User systemAdmin = createUser("prompt-admin-delete", "prompt-admin-delete@example.com", "SYSTEM_ADMIN");
        String templateId = insertInactivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK);

        mockMvc.perform(delete("/api/ai/prompts/{id}", templateId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM prompt_template WHERE id = ?",
                Integer.class,
                templateId);
        if (count == null || count != 0) {
            throw new AssertionError("已停用模板删除后仍然存在");
        }
    }

    /**
     * 验证启用中的模板必须先停用再删除，避免运行中模板被直接物理删除后让读取链路失去兜底配置。
     */
    @Test
    void shouldRejectDeletingActivePromptTemplate() throws Exception {
        User systemAdmin = createUser("prompt-del-act", "prompt-del-act@example.com", "SYSTEM_ADMIN");
        String templateId = insertActivePromptTemplate(PromptTemplateType.RESULT_FEEDBACK);

        mockMvc.perform(delete("/api/ai/prompts/{id}", templateId)
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("启用中的 Prompt 模板不能直接删除，请先停用后再删除"));
    }

    /**
     * 验证删除不存在模板时会返回明确业务异常，避免后台把“目标不存在”和“删除成功”混淆。
     */
    @Test
    void shouldRejectDeletingNonexistentPromptTemplate() throws Exception {
        User systemAdmin = createUser("prompt-del-miss", "prompt-del-miss@example.com", "SYSTEM_ADMIN");

        mockMvc.perform(delete("/api/ai/prompts/{id}", UuidUtil.randomUuid())
                        .header("Authorization", bearer(systemAdmin, "SYSTEM_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prompt 模板不存在"));
    }

    /**
     * 创建指定角色的模板测试用户。
     * <p>
     * 该辅助方法统一创建可正常登录的账号，从而把测试焦点锁定在 `SYSTEM_ADMIN` 可管理模板、`USER` 不可越权访问这一权限契约上，
     * 避免账户冻结、禁用等无关因素干扰模板管理链路验证。
     */
    private User createUser(String username, String email, String roleName) {
        Role role = roleMapper.findByName(roleName);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138141");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    /**
     * 插入一条可更新的 Prompt 模板记录。
     * <p>
     * 这里显式构造 `INTENT_RECOGNITION` 类型和固定代码，目的是保护“更新已有模板”这条链路，
     * 确保测试验证的是更新副作用与返回结果，而不是新建逻辑或类型校验本身。
     */
    private String insertPromptTemplate() {
        String templateId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                """
                        INSERT INTO prompt_template (
                            id, name, code, content, type, description, variables, is_active, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                templateId,
                "意图识别模板-原始",
                "INTENT_PROMPT_FOR_UPDATE",
                "原始模板内容",
                PromptTemplateType.INTENT_RECOGNITION.name(),
                "更新前模板",
                "[\"message\"]",
                1,
                "1.0");
        return templateId;
    }

    /**
     * 插入一条已启用模板，用于验证同类型只能保留一条启用模板的写入侧约束。
     */
    private String insertActivePromptTemplate(PromptTemplateType type) {
        String templateId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                """
                        INSERT INTO prompt_template (
                            id, name, code, content, type, description, variables, is_active, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                templateId,
                type.name() + "-已启用-" + templateId.substring(0, 8),
                type.name() + "_ACTIVE_" + templateId.substring(0, 8),
                "已启用模板",
                type.name(),
                "测试同类型启用冲突",
                "[\"message\"]",
                1,
                "1.0");
        return templateId;
    }

    /**
     * 插入一条已停用模板，用于验证后台允许清理不再生效的历史模板。
     */
    private String insertInactivePromptTemplate(PromptTemplateType type) {
        String templateId = UuidUtil.randomUuid();
        jdbcTemplate.update(
                """
                        INSERT INTO prompt_template (
                            id, name, code, content, type, description, variables, is_active, version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                templateId,
                type.name() + "-已停用-" + templateId.substring(0, 8),
                type.name() + "_INACTIVE_" + templateId.substring(0, 8),
                "已停用模板",
                type.name(),
                "测试停用模板删除",
                "[\"message\"]",
                0,
                "1.0");
        return templateId;
    }

    /**
     * 生成模板管理场景使用的 Bearer Token。
     * <p>
     * 模板接口完全依赖角色鉴权，因此这里保留显式角色参数，确保测试可以精确保护系统管理员放行、普通用户拒绝的安全契约。
     */
    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }
}
