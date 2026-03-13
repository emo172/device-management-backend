# MyBatis-Plus Boot 4 Baseline Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前后端骨架补齐 MyBatis-Plus 持久层基础设施，并把项目工程基线文档统一到 Spring Boot 4.x。

**Architecture:** 保持现有 `Spring Boot 4.0.3 + application.properties` 的最小结构，新增 MyBatis-Plus Boot 4 starter、Mapper 扫描与基础配置，只铺设后续实体/Mapper 所需的运行底座，不提前引入具体业务表映射。同时把仓库内仍声明 `Spring Boot 3.x` 的基线文档统一修正到 `4.x`。

**Tech Stack:** Java 21, Maven, Spring Boot 4.0.3, MyBatis-Plus 3.5.16, MySQL Connector/J

---

## Chunk 1: 持久层底座与文档基线对齐

### Task 1: 补齐 MyBatis-Plus 基础设施

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`

- [ ] **Step 1: 增加 MyBatis-Plus Boot 4 依赖**
- [ ] **Step 2: 增加 Mapper 扫描与 MyBatis-Plus 基础配置**
- [ ] **Step 3: 保持现有数据库连接配置可继续工作**

### Task 2: 同步修正文档工程基线

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-03-11-device-management-implementation-roadmap-design.md`
- Modify: `docs/superpowers/plans/2026-03-11-device-management-backend-implementation-plan.md`

- [ ] **Step 1: 把项目工程基线中的 Spring Boot 3.x 统一改为 4.x**
- [ ] **Step 2: 去掉“当前骨架与目标 3.x 不一致”的旧表述，改成与当前约定一致的描述**

### Task 3: 执行验证

**Files:**
- Test: `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`

- [ ] **Step 1: 运行 `mvn test` 验证上下文仍可启动**
- [ ] **Step 2: 确认测试结果为 `BUILD SUCCESS` 且 `Failures: 0, Errors: 0`**
