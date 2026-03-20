package com.jhun.backend.integration.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

    /**
     * 安全基线测试把上传目录隔离到系统临时目录，避免为了验证公开资源策略污染仓库下的真实 uploads 目录。
     */
    private static final Path TEST_UPLOAD_DIR = createTestUploadDirectory();

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

    /**
     * 把安全测试中的静态资源映射切到独立临时目录，确保断言覆盖真实 `storage.upload-dir` 配置链路。
     */
    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.upload-dir", () -> TEST_UPLOAD_DIR.toString());
    }

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
     * 清理测试阶段写入的临时上传目录，避免安全测试反复运行后堆积静态资源样本。
     */
    @AfterAll
    static void cleanUpUploadDirectory() throws IOException {
        deleteDirectoryRecursively(TEST_UPLOAD_DIR);
    }

    /**
     * 验证匿名用户访问受保护后台接口时会被拒绝，防止安全配置意外放开管理接口。
     */
    @Test
    void shouldRejectAnonymousProtectedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证配置中的允许源会被真实写入预检响应头，
     * 防止只在配置文件声明白名单却没有接入到 Spring MVC / Security 的跨域链路中。
     */
    @Test
    void shouldAllowConfiguredOriginPreflightRequest() throws Exception {
        mockMvc.perform(options("/api/devices")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    /**
     * 验证匿名白名单只放开设备图片目录，而不会把上传根目录下的其他文件一并公开。
     */
    @Test
    void shouldExposeOnlyDeviceImagePathToAnonymousRequests() throws Exception {
        byte[] publicImageBytes = "public-device-image".getBytes();
        writeUploadFile("devices/public-device.png", publicImageBytes);
        writeUploadFile("secret.txt", "private-root-file".getBytes());

        mockMvc.perform(get("/files/devices/public-device.png"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(publicImageBytes));

        mockMvc.perform(get("/files/secret.txt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证资源映射本身也收窄到 `/files/devices/**`，
     * 避免认证用户借助公开资源处理器直接读取上传根目录中的非设备文件。
     */
    @Test
    void shouldNotServeNonDeviceUploadFileEvenForAuthenticatedUser() throws Exception {
        writeUploadFile("secret.txt", "private-root-file".getBytes());
        String token = bearer(createNormalUser("sec-user-1", "sec-user-1@example.com"), "USER");

        MvcResult result = mockMvc.perform(get("/files/secret.txt")
                        .header("Authorization", token))
                .andReturn();

        /*
         * 这里不强绑具体状态码，而是直接保护“非设备目录文件绝不能被成功读取”的资源边界；
         * 只要资源处理链已经不再返回 200 与原始文件内容，就说明上传根目录没有继续被静态映射透出。
         */
        org.junit.jupiter.api.Assertions.assertNotEquals(200, result.getResponse().getStatus());
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
        user.setPhone("13800138111");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private String bearer(User user, String role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), role);
    }

    /**
     * 按相对路径写入测试用静态资源，模拟上传根目录下不同子目录的真实文件布局。
     */
    private void writeUploadFile(String relativePath, byte[] bytes) throws IOException {
        Path target = TEST_UPLOAD_DIR.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private static Path createTestUploadDirectory() {
        try {
            return Files.createTempDirectory("security-bootstrap-it-uploads-");
        } catch (IOException exception) {
            throw new IllegalStateException("创建安全测试上传目录失败", exception);
        }
    }

    private static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
