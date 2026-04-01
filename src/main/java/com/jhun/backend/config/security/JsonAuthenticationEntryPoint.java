package com.jhun.backend.config.security;

import com.jhun.backend.common.response.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.json.JsonWriter;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 统一未认证响应入口。
 * <p>
 * 控制层异常可以交给全局异常处理器统一包装，但过滤器链中的未认证场景不会进入控制层；
 * 因此这里先提供最小 JSON 出口，为后续把匿名请求、过期令牌和非法令牌统一翻译成 `Result.error(...)` 结构预留抓手。
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final JsonWriter<Result<Void>> RESULT_JSON_WRITER = JsonWriter.of((members) -> {
        members.add("code", Result::getCode);
        members.add("message", Result::getMessage);
        members.add("data", Result::getData);
    });

    private static final String UNAUTHORIZED_MESSAGE = "未登录或登录已过期，请重新登录";

    /**
     * 以项目统一响应壳返回 401，避免安全层回退成裸状态码或散装 JSON。
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(RESULT_JSON_WRITER.writeToString(Result.error(UNAUTHORIZED_MESSAGE)));
    }
}
