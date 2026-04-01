package com.jhun.backend.config.security;

import com.jhun.backend.service.support.auth.AuthRuntimeStateSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 鉴权过滤器。
 * <p>
 * 负责从 Bearer Token 中恢复当前登录人身份，使 `/api/auth/me`、`/api/auth/profile` 和后续管理接口能够基于同一安全上下文工作。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthRuntimeStateSupport authRuntimeStateSupport;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            AuthRuntimeStateSupport authRuntimeStateSupport,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authRuntimeStateSupport = authRuntimeStateSupport;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorization.substring(7);
            Claims claims;

            /*
             * 这里只把 JWT 自身的解析失败翻译成统一 401 JSON。
             * 一旦令牌已经解析成功，后续的会话触达、鉴权上下文装配以及控制层/基础设施异常
             * 都必须继续按原有 400/500 语义暴露，避免把真实故障误吞成“登录过期”。
             */
            try {
                claims = jwtTokenProvider.parseClaims(token);
            } catch (JwtException | IllegalArgumentException exception) {
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("JWT 无效或已过期", exception));
                return;
            }

            /*
             * 访问受保护接口时只能接受 access token。
             * refresh token 只允许出现在换发链路，若在这里放行会直接绕过短时效访问令牌的安全边界。
             */
            String tokenType = claims.get("tokenType", String.class);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            AuthUserPrincipal principal = new AuthUserPrincipal(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("role", String.class));

            /*
             * 访问令牌是当前请求链路中最稳定、且无需额外存储前置就能识别的会话键。
             * 这里在鉴权成功后刷新其最近活跃时间，让 C-10 真正按“请求空闲窗口”治理会话；
             * 但即使没有对应快照，也不会阻断本次请求，从而保持现有 JWT 测试基线不变。
             */
            authRuntimeStateSupport.touchOrCreateSession(token, claims.getSubject(), LocalDateTime.now());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
