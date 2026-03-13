package com.jhun.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动入口。
 * <p>
 * 当前阶段仅负责加载 Spring Boot、MyBatis-Plus Mapper 扫描和基础配置，
 * 后续所有业务模块都继续挂载在 {@code com.jhun.backend} 包根下，避免与真相源约定冲突。
 */
@MapperScan("com.jhun.backend.mapper")
@SpringBootApplication
public class DeviceManagementBackendApplication {

    /**
     * 启动后端应用上下文。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DeviceManagementBackendApplication.class, args);
    }

}
