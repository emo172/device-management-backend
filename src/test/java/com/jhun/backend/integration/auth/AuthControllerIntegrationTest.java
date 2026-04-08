package com.jhun.backend.integration.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.config.security.JwtProperties;
import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 认证控制器集成测试。
 * <p>
 * 该测试覆盖注册、登录、查询本人信息与修改个人资料的最小闭环，
 * 用于验证认证接口已经具备前后端联调所需的基本行为。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthRuntimeStateSupport authRuntimeStateSupport;

    @Autowired
    private JwtProperties jwtProperties;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证注册后可以使用登录结果中的 Bearer Token 查询当前用户信息。
     */
    @Test
    void shouldReturnCurrentUserProfileAfterLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhaoliu",
                                  "password": "Password123!",
                                  "email": "zhaoliu@example.com",
                                  "realName": "赵六",
                                  "phone": "13800138003"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "zhaoliu",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginBody.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("zhaoliu"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    /**
     * 验证本地浏览器从 `127.0.0.1` 发起登录时不会被默认跨域白名单误伤。
     * <p>
     * 真实联调里前端开发服务器可能因为端口占用切到 `5174`，浏览器仍会携带 `Origin`，
     * 因此这里直接用登录接口锁住 `127.0.0.1` + Vite 常用端口的跨域放行语义，防止 `/ai` 页面在进入认证前就被 403 拦截。
     */
    @Test
    void shouldAllowLoginRequestFromLoopbackViteOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "cors-login-user",
                                  "password": "Password123!",
                                  "email": "cors-login-user@example.com",
                                  "realName": "跨域登录用户",
                                  "phone": "13800138018"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:5174")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "cors-login-user",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    /**
     * 验证已登录用户可以修改个人资料，保护“个人中心”页依赖的更新能力。
     */
    @Test
    void shouldUpdateProfileForCurrentUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "sunqi",
                                  "password": "Password123!",
                                  "email": "sunqi@example.com",
                                  "realName": "孙七",
                                  "phone": "13800138004"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "sunqi@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginBody.path("data").path("accessToken").asText();

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "孙七-已更新",
                                  "phone": "13800138099"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("孙七-已更新"))
                .andExpect(jsonPath("$.data.phone").value("13800138099"));
    }

    /**
     * 验证刷新令牌不能直接访问受保护接口，避免绕过 access token 的短时效约束。
     */
    @Test
    void shouldRejectRefreshTokenWhenAccessingProtectedEndpoint() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhoujiu",
                                  "password": "Password123!",
                                  "email": "zhoujiu@example.com",
                                  "realName": "周九",
                                  "phone": "13800138011"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "zhoujiu",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginBody.path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 验证 access token 过期后访问当前用户信息接口会返回 401，
     * 为后续补齐 JWT 过滤器异常翻译缺口提供稳定的安全回归抓手。
     */
    @Test
    void shouldRejectExpiredAccessTokenWhenAccessingCurrentUserProfile() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "expired-access-user",
                                  "password": "Password123!",
                                  "email": "expired-access-user@example.com",
                                  "realName": "过期访问令牌用户",
                                  "phone": "13800138012"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "expired-access-user",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginBody.path("data").path("accessToken").asText();
        String expiredAccessToken = createExpiredAccessToken(accessToken);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 验证访问受保护接口时会刷新访问令牌对应的最近活跃时间，确保 C-10 的“空闲会话”语义基于真实请求触达。
     */
    @Test
    void shouldTouchSessionWhenAccessingProtectedEndpoint() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "touch-user",
                                  "password": "Password123!",
                                  "email": "touch-user@example.com",
                                  "realName": "会话触达用户",
                                  "phone": "13800138013"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "touch-user",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginBody.path("data").path("accessToken").asText();

        authRuntimeStateSupport.cleanupTimedOutSessions(LocalDateTime.now().plusMinutes(31), 30);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        authRuntimeStateSupport.cleanupTimedOutSessions(LocalDateTime.now().plusMinutes(29), 30);

        org.junit.jupiter.api.Assertions.assertTrue(authRuntimeStateSupport.hasSession(accessToken));
    }

    /**
     * 基于真实 access token 的载荷和测试环境 JWT 配置重签一个已过期令牌，
     * 确保该测试命中的是真实“过期 access token”分支，而不是签名非法或 tokenType 错误分支。
     */
    private String createExpiredAccessToken(String accessToken) {
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(claims.getSubject())
                .claim("username", claims.get("username", String.class))
                .claim("role", claims.get("role", String.class))
                .claim("tokenType", claims.get("tokenType", String.class))
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
