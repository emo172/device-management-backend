package com.jhun.backend.config.web;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 基础设施配置。
 * <p>
 * 该配置统一承接两类前后端联调基础能力：
 * 一是把 `application.yml` 中声明的跨域白名单真实接入 Spring MVC，避免出现“配置写了但浏览器预检仍失败”的假放行；
 * 二是把本地上传目录统一映射到 `/files/**`，让设备图片等静态资源拥有稳定出口。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
    private static final String DEVICE_IMAGE_DIRECTORY = "devices";

    private final String[] allowedOrigins;
    private final Path uploadRoot;

    public WebMvcConfig(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${storage.upload-dir:uploads}") String uploadDir) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 注册全局跨域规则。
     * <p>
     * 这里使用配置驱动的白名单，而不是把来源硬编码到安全配置里，
     * 是为了让预检放行、正式响应头和后续环境切换都共用同一真相源。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册本地上传目录的静态资源映射。
     * <p>
     * 设备详情与列表当前只需要匿名读取设备图片，
     * 因此这里只把 `<upload-dir>/devices/` 暴露到 `/files/devices/**`，
     * 避免把上传根目录中的其他业务文件也一并变成公开静态资源。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/devices/**")
                .addResourceLocations(toDirectoryResourceLocation(uploadRoot.resolve(DEVICE_IMAGE_DIRECTORY)));
    }

    /**
     * Spring 静态资源位置需要显式以目录语义结尾，
     * 否则不同运行环境下可能把上传根目录当作单文件位置解析。
     */
    private String toDirectoryResourceLocation(Path directory) {
        String resourceLocation = directory.toUri().toString();
        return resourceLocation.endsWith("/") ? resourceLocation : resourceLocation + "/";
    }
}
