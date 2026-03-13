package com.jhun.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.jhun.backend.mapper")
@SpringBootApplication
public class DeviceManagementBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceManagementBackendApplication.class, args);
    }

}
