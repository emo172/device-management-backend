# MySQL Datasource Config Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前 Spring Boot 后端骨架补齐本地 MySQL `device_management` 的最小可用连接配置。

**Architecture:** 保持现有 `application.properties` 单文件配置方式，在不扩散 profile 复杂度的前提下，补充 Spring JDBC 与 MySQL 驱动依赖，并写入 `spring.datasource.*` 参数。当前仅满足本地开发直连数据库，后续如需区分环境再拆分 profile 或环境变量。

**Tech Stack:** Java 21, Maven, Spring Boot, Spring JDBC, MySQL Connector/J

---

## Chunk 1: 本地数据库连接最小落地

### Task 1: 补齐依赖与配置

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 补充数据源依赖**

在 `pom.xml` 中增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: 写入本地 MySQL 连接配置**

在 `src/main/resources/application.properties` 中增加：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/device_management?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=13098459921wct
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

- [ ] **Step 3: 运行最小验证**

Run: `mvn test`

Expected: Spring 上下文可完成启动，说明数据源配置与依赖解析正常；若 MySQL 服务未启动或库不存在，则测试会因连接失败报错，需要先处理本地数据库环境。
