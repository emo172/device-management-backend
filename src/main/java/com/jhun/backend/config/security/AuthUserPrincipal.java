package com.jhun.backend.config.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 认证用户主体。
 * <p>
 * 安全过滤链解析 JWT 后，将用户 ID、用户名和角色名组装为统一主体，供控制层和业务层读取当前登录人信息。
 */
public record AuthUserPrincipal(String userId, String username, String role) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }
}
