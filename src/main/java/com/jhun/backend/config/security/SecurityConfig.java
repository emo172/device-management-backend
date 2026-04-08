package com.jhun.backend.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全基线配置。
 * <p>
 * 当前阶段先建立“白名单最小放行、其余接口默认鉴权”的基线，满足计划中阶段 0 对安全链路的要求；
 * 后续接入 JWT、角色权限与细粒度授权时，仍以该配置类作为统一入口继续扩展。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
    }

    /**
     * 构建默认安全过滤链。
     * <p>
     * 这里显式关闭 CSRF 与表单登录，启用无状态会话策略，并把匿名访问失败统一映射为 401，
     * 以匹配前后端分离接口的 Bearer Token 鉴权模型。
     *
     * @param http Spring Security HTTP 配置对象
     * @return 安全过滤链
     * @throws Exception 配置构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(withDefaults())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jsonAuthenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        /*
                         * 预检请求和 `/files/devices/**` 都属于前后端联调基础设施：
                         * 前者要先于鉴权完成浏览器协商，后者只承载设备图片公开访问，
                         * 因此这里必须精确放行设备图片目录，而不能继续把整个上传根目录匿名暴露出去。
                         */
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verification-code",
                                "/api/auth/reset-password",
                                "/api/internal/seeds/reservation-create",
                                "/files/devices/**",
                                "/error")
                        .permitAll()
                        .requestMatchers("/api/admin/users/**", "/api/admin/roles/**").hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 提供密码编码器。
     * <p>
     * 真相源要求密码以 BCrypt 哈希形式存储，因此基础设施阶段即固定为 BCrypt，
     * 避免后续用户、密码历史与重置逻辑出现算法不一致问题。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
