# Device Management Backend Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于当前 Spring Boot 初始骨架，按 `AGENTS.md` 的模块边界与工程规范，分阶段交付设备管理后端项目，并在每完成一部分功能或模块后执行代码审查与功能测试。

**Architecture:** 采用 `Controller -> Service -> Mapper` 三层架构，以 `common` 基础设施、统一响应与统一异常作为底座，优先完成认证权限与主数据，再逐步实现预约、借还、逾期、AI、统计和联调发布能力。整个实施过程严格按阶段推进，每个阶段都必须满足入口条件、代码审查、测试门禁和退出条件后才能继续下一阶段。

**Tech Stack:** Java 21, Spring Boot, Spring Security, MyBatis-Plus, MySQL, Redis, JWT, Spring AI, Maven, JUnit 5, MockMvc, Lombok

---

## 0. 当前上下文

- 当前仓库只有 `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`、`src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`、`src/main/resources/application.properties` 和最小化 `pom.xml`
- 当前包路径为 `com.jhun.backend`，与 `AGENTS.md` 约定的 `com.jhu.device.management` 不一致
- 当前尚未建立 `config/common/dto/entity/mapper/service/controller/scheduler` 目标结构
- 当前尚未具备 MyBatis-Plus、Spring Security、Redis、JWT、AI、定时任务、邮件通知等实施基础
- 该计划按“总路线图 + 分块执行”编写，每个块都可单独交付和验收

## 1. 执行约束

- 所有实现基于 `AGENTS.md`，默认在功能分支执行，分支命名遵循 `feature/*`、`fix/*`、`refactor/*`
- 执行每个 Task 时，优先使用 `@superpowers:test-driven-development` 思路：先写失败测试，再补最小实现，再回归
- 每个 Chunk 结束前，必须调用 `@superpowers:requesting-code-review` 做阶段性代码审查
- 每次准备宣布“完成”前，必须按 `@superpowers:verification-before-completion` 执行验证命令并核对输出
- 未通过当前 Chunk 的退出条件前，不得开始下一个 Chunk
- 除非用户明确要求，否则只在计划中写提交命令，不在当前会话实际提交

## 2. 目标文件地图

### 2.1 基础工程与通用能力

- Modify: `pom.xml`
- Move: `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java` -> `src/main/java/com/jhu/device/management/DeviceManagementApplication.java`
- Move: `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java` -> `src/test/java/com/jhu/device/management/DeviceManagementApplicationTests.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `src/main/resources/application-prod.yml`
- Create: `src/main/java/com/jhu/device/management/config/AppConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/AsyncConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ThreadPoolConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/WebConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/RedisConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/email/EmailConfig.java`
- Modify: `src/main/java/com/jhu/device/management/config/ai/AiConfig.java`
- Modify: `src/main/java/com/jhu/device/management/config/ai/PromptConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/mybatis/MybatisPlusConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/mybatis/PaginationInterceptor.java`
- Create: `src/main/java/com/jhu/device/management/config/security/SecurityConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtAuthenticationEntryPoint.java`
- Create: `src/main/java/com/jhu/device/management/config/security/AccessDeniedHandlerImpl.java`
- Create: `src/main/java/com/jhu/device/management/common/response/ApiResponse.java`
- Create: `src/main/java/com/jhu/device/management/common/response/PageResponse.java`
- Create: `src/main/java/com/jhu/device/management/common/exception/BusinessException.java`
- Create: `src/main/java/com/jhu/device/management/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/jhu/device/management/common/entity/BaseEntity.java`
- Create: `src/main/java/com/jhu/device/management/common/constant/RedisKeyConstants.java`
- Create: `src/main/java/com/jhu/device/management/common/constant/ErrorConstants.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/DeviceStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/ReservationStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/CheckInStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/FreezeStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/NotificationType.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/NotificationStatus.java`
- Create: `src/main/java/com/jhu/device/management/entity/NotificationRecord.java`
- Create: `src/main/java/com/jhu/device/management/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/EmailSender.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/support/SchedulerExecutionSupport.java`
- Create: `src/main/java/com/jhu/device/management/util/JwtUtil.java`
- Create: `src/main/resources/sql/01_core_schema.sql`
- Create: `src/main/resources/sql/02_seed_admin.sql`
- Create: `src/test/java/com/jhu/device/management/unit/config/ApplicationProfileConfigTest.java`
- Create: `src/test/java/com/jhu/device/management/unit/common/GlobalExceptionHandlerTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/security/SecurityBootstrapIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/support/BaseIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/Stage0SmokeIT.java`

### 2.2 用户、权限与通知

- Create: `src/main/java/com/jhu/device/management/entity/User.java`
- Create: `src/main/java/com/jhu/device/management/entity/Role.java`
- Create: `src/main/java/com/jhu/device/management/entity/Permission.java`
- Create: `src/main/java/com/jhu/device/management/entity/RolePermission.java`
- Modify: `src/main/java/com/jhu/device/management/entity/NotificationRecord.java`
- Create: `src/main/java/com/jhu/device/management/controller/AuthController.java`
- Create: `src/main/java/com/jhu/device/management/controller/UserController.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/LoginRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/RegisterRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/RefreshTokenRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/SendVerificationCodeRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/ResetPasswordRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/LoginResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/VerificationCodeResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/ResetTokenResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/CreateUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UpdateUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/QueryUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UserResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/FreezeUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UnfreezeUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/AssignPermissionRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/notification/NotificationResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/UserMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/RoleMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/PermissionMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/RolePermissionMapper.java`
- Modify: `src/main/java/com/jhu/device/management/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/UserMapper.xml`
- Create: `src/main/resources/mapper/RoleMapper.xml`
- Create: `src/main/resources/mapper/PermissionMapper.xml`
- Create: `src/main/resources/mapper/RolePermissionMapper.xml`
- Modify: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/AuthService.java`
- Create: `src/main/java/com/jhu/device/management/service/UserService.java`
- Create: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/EmailSender.java`
- Create: `src/main/java/com/jhu/device/management/service/support/NotificationTemplate.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/system/TokenCleanupProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/system/SessionTimeoutProcessor.java`
- Create: `src/test/java/com/jhu/device/management/unit/service/AuthServiceTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/auth/AuthControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/user/UserControllerIntegrationTest.java`

### 2.3 设备与分类

- Create: `src/main/java/com/jhu/device/management/entity/Device.java`
- Create: `src/main/java/com/jhu/device/management/entity/DeviceCategory.java`
- Create: `src/main/java/com/jhu/device/management/entity/DeviceStatusChangeLog.java`
- Create: `src/main/java/com/jhu/device/management/controller/DeviceController.java`
- Create: `src/main/java/com/jhu/device/management/controller/CategoryController.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/CreateDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/UpdateDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/QueryDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/DeviceResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/CreateCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/UpdateCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/QueryCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/CategoryResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceCategoryMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceStatusChangeLogMapper.java`
- Create: `src/main/resources/mapper/DeviceMapper.xml`
- Create: `src/main/resources/mapper/DeviceCategoryMapper.xml`
- Create: `src/main/resources/mapper/DeviceStatusChangeLogMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/DeviceService.java`
- Create: `src/main/java/com/jhu/device/management/service/CategoryService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/CategoryServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/DeviceImageStorageSupport.java`
- Create: `src/test/java/com/jhu/device/management/unit/service/DeviceServiceTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/device/DeviceControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/category/CategoryControllerIntegrationTest.java`

### 2.4 预约、借还与逾期

- Create: `src/main/java/com/jhu/device/management/entity/Reservation.java`
- Create: `src/main/java/com/jhu/device/management/entity/BorrowRecord.java`
- Create: `src/main/java/com/jhu/device/management/entity/OverdueRecord.java`
- Create: `src/main/java/com/jhu/device/management/controller/ReservationController.java`
- Create: `src/main/java/com/jhu/device/management/controller/BorrowController.java`
- Create: `src/main/java/com/jhu/device/management/controller/OverdueController.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/CreateReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/UpdateReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/QueryReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ReservationResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/AuditReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/CheckInRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ManualProcessRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ConflictCheckResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/ConfirmBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/ReturnBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/QueryBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/BorrowResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/HandleOverdueRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/QueryOverdueRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/OverdueResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/OverdueStatisticsResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/ReservationMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/BorrowRecordMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/OverdueRecordMapper.java`
- Create: `src/main/resources/mapper/ReservationMapper.xml`
- Create: `src/main/resources/mapper/BorrowRecordMapper.xml`
- Create: `src/main/resources/mapper/OverdueRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/ReservationService.java`
- Create: `src/main/java/com/jhu/device/management/service/BorrowService.java`
- Create: `src/main/java/com/jhu/device/management/service/OverdueService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/ReservationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/BorrowServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/OverdueServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/ReservationValidator.java`
- Create: `src/main/java/com/jhu/device/management/service/support/ConflictDetector.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAuditTimeoutReminder.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAutoExpireProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationCheckInTimeoutProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/BorrowConfirmTimeoutProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationUpcomingReminder.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueAutoDetectProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueNotificationProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueRestrictionReleaseProcessor.java`
- Create: `src/test/java/com/jhu/device/management/unit/service/support/ReservationValidatorTest.java`
- Create: `src/test/java/com/jhu/device/management/unit/service/support/ConflictDetectorTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/reservation/ReservationControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/borrow/BorrowControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/overdue/OverdueControllerIntegrationTest.java`

### 2.5 AI、统计与发布

- Create: `src/main/java/com/jhu/device/management/entity/ChatHistory.java`
- Create: `src/main/java/com/jhu/device/management/entity/PromptTemplate.java`
- Create: `src/main/java/com/jhu/device/management/entity/StatisticsDaily.java`
- Create: `src/main/java/com/jhu/device/management/controller/AiController.java`
- Create: `src/main/java/com/jhu/device/management/controller/StatisticsController.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiChatRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiChatResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiIntentResult.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiSlotInfo.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/CreatePromptTemplateRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/UpdatePromptTemplateRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/PromptTemplateResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/QueryStatisticsRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/OverviewResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/DeviceUsageResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/BorrowStatsResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/HotTimeSlotResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/ChatHistoryMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/PromptTemplateMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/StatisticsDailyMapper.java`
- Create: `src/main/resources/mapper/ChatHistoryMapper.xml`
- Create: `src/main/resources/mapper/PromptTemplateMapper.xml`
- Create: `src/main/resources/mapper/StatisticsDailyMapper.xml`
- Create: `src/main/java/com/jhu/device/management/config/ai/AiConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ai/PromptConfig.java`
- Create: `src/main/java/com/jhu/device/management/service/AiService.java`
- Create: `src/main/java/com/jhu/device/management/service/PromptTemplateService.java`
- Create: `src/main/java/com/jhu/device/management/service/StatisticsService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/AiServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/PromptTemplateServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/StatisticsServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/IntentRecognizer.java`
- Create: `src/main/java/com/jhu/device/management/service/support/SlotExtractor.java`
- Create: `src/main/java/com/jhu/device/management/service/support/PromptEngine.java`
- Create: `src/main/java/com/jhu/device/management/service/support/LlmClient.java`
- Create: `src/main/java/com/jhu/device/management/service/support/LlmClientImpl.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/statistics/StatisticsAggregationProcessor.java`
- Create: `src/main/resources/templates/email/verify-code.html`
- Create: `src/main/resources/templates/email/reservation-reminder.html`
- Create: `src/main/resources/templates/email/overdue-warning.html`
- Create: `src/main/resources/templates/ai/device-query.txt`
- Create: `src/main/resources/templates/ai/reservation-audit.txt`
- Create: `src/main/resources/logback-spring.xml`
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `README.md`
- Create: `src/test/java/com/jhu/device/management/integration/ai/AiControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/statistics/StatisticsControllerIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/system/EndToEndSmokeIntegrationTest.java`

## Chunk 1: 基础工程与通用能力

**Milestone Goal:** 把当前初始骨架升级为符合 `AGENTS.md` 的可扩展工程底座，为后续业务模块提供统一的包结构、配置、异常、响应、安全与测试基础。

**Entry Condition:** 当前仓库仅能启动 Spring Boot 上下文，尚未具备真实业务实施基础。

**Exit Condition:** 已完成包结构对齐、依赖基线、环境配置、通用响应异常、安全与持久化基础接入，且阶段 0 测试与代码审查通过。

### Task 1: 对齐启动类、包路径与依赖基线

**Files:**
- Modify: `pom.xml`
- Move: `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java` -> `src/main/java/com/jhu/device/management/DeviceManagementApplication.java`
- Move: `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java` -> `src/test/java/com/jhu/device/management/DeviceManagementApplicationTests.java`
- Test: `src/test/java/com/jhu/device/management/DeviceManagementApplicationTests.java`

- [ ] **Step 1: 先写新的上下文加载测试，锁定目标包路径**

```java
@SpringBootTest(classes = DeviceManagementApplication.class)
@ActiveProfiles("test")
class DeviceManagementApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: 运行测试，确认当前因类名或包路径不匹配而失败**

Run: `mvn test -Dtest=DeviceManagementApplicationTests`
Expected: FAIL，提示 `DeviceManagementApplication` 不存在或上下文加载失败

- [ ] **Step 3: 迁移启动类并补齐阶段 0 所需依赖基线**

```java
package com.jhu.device.management;

@SpringBootApplication
public class DeviceManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceManagementApplication.class, args);
    }
}
```

- [ ] **Step 4: 在 `pom.xml` 中补齐最小实施依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

- [ ] **Step 5: 重新运行测试并确认通过**

Run: `mvn test -Dtest=DeviceManagementApplicationTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 记录一次基础设施提交**

```bash
git add pom.xml src/main/java/com/jhu/device/management/DeviceManagementApplication.java src/test/java/com/jhu/device/management/DeviceManagementApplicationTests.java
git commit -m "chore(core): align bootstrap package and dependency baseline"
```

### Task 2: 建立分环境配置与基础配置类

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `src/main/resources/application-prod.yml`
- Create: `src/main/java/com/jhu/device/management/config/AppConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/AsyncConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ThreadPoolConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/WebConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/email/EmailConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ai/AiConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ai/PromptConfig.java`
- Test: `src/test/java/com/jhu/device/management/unit/config/ApplicationProfileConfigTest.java`

- [ ] **Step 1: 先写配置绑定测试，约束主配置结构**

```java
@SpringBootTest
@ActiveProfiles("test")
class ApplicationProfileConfigTest {

    @Value("${jwt.access-token-validity}")
    private Long accessTokenValidity;

    @Test
    void shouldLoadJwtConfigFromProfile() {
        assertThat(accessTokenValidity).isPositive();
    }
}
```

- [ ] **Step 2: 运行测试，确认配置项尚未存在**

Run: `mvn test -Dtest=ApplicationProfileConfigTest`
Expected: FAIL，提示找不到 `jwt.access-token-validity`

- [ ] **Step 3: 写入主配置和多环境配置骨架**

```yaml
spring:
  application:
    name: device-management-backend
  profiles:
    active: dev
  mail:
    host: smtp.example.com

jwt:
  access-token-validity: 86400
  refresh-token-validity: 604800

ai:
  provider: qianwen
  model: qwen-turbo
```

- [ ] **Step 4: 创建基础配置类，锁定线程池、异步和 CORS 入口**

```java
@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 4A: 创建邮件配置预留，锁定通知基础设施入口**

```java
@Configuration
public class EmailConfig {
}
```

- [ ] **Step 4B: 创建 AI 配置与 Prompt 配置占位，锁定后续 AI 接入入口**

```java
@Configuration
public class AiConfig {
}
```

- [ ] **Step 5: 回归配置测试**

Run: `mvn test -Dtest=ApplicationProfileConfigTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交配置基线**

```bash
git add src/main/resources/application.yml src/main/resources/application-dev.yml src/main/resources/application-test.yml src/main/resources/application-prod.yml src/main/java/com/jhu/device/management/config src/test/java/com/jhu/device/management/unit/config/ApplicationProfileConfigTest.java
git commit -m "chore(config): add profile configuration baseline"
```

### Task 3: 建立统一响应、统一异常与公共基础类型

**Files:**
- Create: `src/main/java/com/jhu/device/management/common/response/ApiResponse.java`
- Create: `src/main/java/com/jhu/device/management/common/response/PageResponse.java`
- Create: `src/main/java/com/jhu/device/management/common/exception/BusinessException.java`
- Create: `src/main/java/com/jhu/device/management/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/jhu/device/management/common/entity/BaseEntity.java`
- Create: `src/main/java/com/jhu/device/management/common/constant/ErrorConstants.java`
- Create: `src/main/java/com/jhu/device/management/common/constant/RedisKeyConstants.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/DeviceStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/ReservationStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/CheckInStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/FreezeStatus.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/NotificationType.java`
- Create: `src/main/java/com/jhu/device/management/common/enums/NotificationStatus.java`
- Test: `src/test/java/com/jhu/device/management/unit/common/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 先写异常处理测试，定义统一响应协议**

```java
@WebMvcTest
class GlobalExceptionHandlerTest {

    @Test
    void shouldWrapBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }
}
```

- [ ] **Step 2: 运行测试，确认响应封装尚未存在**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL，提示全局异常处理器或响应结构不存在

- [ ] **Step 3: 实现统一响应与异常处理最小版本**

```java
public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "ok", data);
    }
}
```

- [ ] **Step 4: 补齐 `BaseEntity`、错误码和 Redis 键常量骨架**

```java
public enum FreezeStatus {
    NORMAL,
    RESTRICTED,
    FROZEN
}
```

```java
public abstract class BaseEntity {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: 回归异常处理测试**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交通用层基线**

```bash
git add src/main/java/com/jhu/device/management/common src/test/java/com/jhu/device/management/unit/common/GlobalExceptionHandlerTest.java
git commit -m "feat(common): add response and exception baseline"
```

### Task 4: 接入安全、JWT、MyBatis-Plus 与 Redis 基础能力

**Files:**
- Create: `src/main/java/com/jhu/device/management/config/security/SecurityConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/jhu/device/management/config/security/JwtAuthenticationEntryPoint.java`
- Create: `src/main/java/com/jhu/device/management/config/security/AccessDeniedHandlerImpl.java`
- Create: `src/main/java/com/jhu/device/management/config/mybatis/MybatisPlusConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/mybatis/PaginationInterceptor.java`
- Create: `src/main/java/com/jhu/device/management/config/RedisConfig.java`
- Modify: `src/main/java/com/jhu/device/management/config/email/EmailConfig.java`
- Modify: `src/main/java/com/jhu/device/management/config/ai/AiConfig.java`
- Modify: `src/main/java/com/jhu/device/management/config/ai/PromptConfig.java`
- Create: `src/main/java/com/jhu/device/management/util/JwtUtil.java`
- Test: `src/test/java/com/jhu/device/management/integration/security/SecurityBootstrapIntegrationTest.java`

- [ ] **Step 1: 先写安全集成测试，锁定匿名与受保护路径行为**

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBootstrapIntegrationTest {

    @Test
    void shouldRejectProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行测试，确认当前安全链未建立**

Run: `mvn test -Dtest=SecurityBootstrapIntegrationTest`
Expected: FAIL，返回 404、200 或上下文缺少安全相关 Bean

- [ ] **Step 3: 搭建最小可用的 JWT 安全链与持久化配置**

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .build();
}
```

- [ ] **Step 4: 补齐 `JwtUtil`、Redis 连接配置和 MyBatis-Plus 基础分页配置**

```java
public String generateAccessToken(String userId) {
    return Jwts.builder().subject(userId).compact();
}
```

- [ ] **Step 4A: 预留 MySQL、邮件和 AI 基础接线点**

```java
@Configuration
public class PaginationInterceptor {
}
```

- [ ] **Step 5: 回归安全集成测试**

Run: `mvn test -Dtest=SecurityBootstrapIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交安全与持久化底座**

```bash
git add src/main/java/com/jhu/device/management/config src/main/java/com/jhu/device/management/util/JwtUtil.java src/test/java/com/jhu/device/management/integration/security/SecurityBootstrapIntegrationTest.java
git commit -m "feat(core): add security persistence and redis bootstrap"
```

### Task 5: 建立 SQL 基线、测试支撑并完成阶段 0 门禁

**Files:**
- Create: `src/main/resources/sql/01_core_schema.sql`
- Create: `src/main/resources/sql/02_seed_admin.sql`
- Create: `src/main/java/com/jhu/device/management/entity/NotificationRecord.java`
- Create: `src/main/java/com/jhu/device/management/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/EmailSender.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/support/SchedulerExecutionSupport.java`
- Create: `src/test/java/com/jhu/device/management/integration/support/BaseIntegrationTest.java`
- Create: `src/test/java/com/jhu/device/management/integration/Stage0SmokeIT.java`

- [ ] **Step 1: 先写阶段 0 冒烟测试，要求上下文、数据库脚本与安全基线同时可用**

```java
class Stage0SmokeIT extends BaseIntegrationTest {

    @Test
    void shouldLoadApplicationContext() {
        assertThat(applicationContext).isNotNull();
    }
}
```

- [ ] **Step 2: 运行冒烟测试，确认测试支撑尚未完整**

Run: `mvn test -Dtest=Stage0SmokeIT`
Expected: FAIL，提示测试基座或数据初始化未完成

- [ ] **Step 3: 创建核心表初始脚本和集成测试基类**

```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY,
    email VARCHAR(128) NOT NULL,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE notification_record (
    id BIGINT PRIMARY KEY,
    recipient VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL
);
```

- [ ] **Step 4: 回归 `Stage0SmokeIT`，确认测试基座已经可用**

Run: `mvn test -Dtest=Stage0SmokeIT`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 执行阶段 0 自检清单**

```text
检查配置文件是否齐全、公共异常与响应是否可加载、NotificationRecord 与通知服务占位是否已预留、调度支撑是否已建立、测试基座是否可跑通。
```

- [ ] **Step 6: 使用 `@superpowers:requesting-code-review` 执行阶段 0 代码审查**

Run: `git diff -- src/main/java/com/jhu/device/management src/main/resources src/test/java/com/jhu/device/management`
Expected: 能明确审查三层结构入口、命名、统一响应、配置结构、安全链、测试基座、公共枚举和邮件预留

- [ ] **Step 7: 修复审查问题并回归阶段 0 指定测试集**

Run: `mvn test -Dtest=DeviceManagementApplicationTests,ApplicationProfileConfigTest,GlobalExceptionHandlerTest,SecurityBootstrapIntegrationTest,Stage0SmokeIT`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: 执行阶段 0 测试门禁**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`

- [ ] **Step 9: 执行完整校验与可选静态检查**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 10: 如质量插件已接入，追加静态检查**

Run: `mvn checkstyle:check && mvn spotbugs:check`
Expected: `BUILD SUCCESS` 或因插件尚未接入而暂缓执行

- [ ] **Step 11: 记录阶段 0 里程碑提交**

```bash
git add src/main/java/com/jhu/device/management/config src/main/java/com/jhu/device/management/common src/main/java/com/jhu/device/management/util/JwtUtil.java src/main/resources src/test/java/com/jhu/device/management
git commit -m "chore(stage0): establish project foundation and test baseline"
```

**Chunk 1 Code Review Checklist:**

- [ ] 包路径与目录是否已对齐 `AGENTS.md`
- [ ] 三层结构入口是否已就位，后续模块无需再改根路径
- [ ] 是否已形成统一响应和统一异常处理入口
- [ ] 安全配置是否只放行 `/api/auth/**`
- [ ] MySQL、Redis、JWT、MyBatis-Plus、Spring AI、邮件依赖是否已建立基线
- [ ] Redis、JWT、MyBatis-Plus 是否以最小可运行方式接入
- [ ] 公共枚举、常量与基础实体是否已建立且可复用
- [ ] 配置项是否按 `application.yml`、`application-dev.yml`、`application-test.yml`、`application-prod.yml` 分环境拆分
- [ ] 邮件/通知基础设施入口是否已预留
- [ ] NotificationRecord 最小模型和调度基础支撑是否已预留
- [ ] 测试基座是否可支撑后续集成测试

**Chunk 1 Test Gate:**

- [ ] `mvn test -Dtest=DeviceManagementApplicationTests,ApplicationProfileConfigTest,GlobalExceptionHandlerTest,SecurityBootstrapIntegrationTest,Stage0SmokeIT`
- [ ] `mvn clean test`
- [ ] `mvn clean verify`
- [ ] `mvn checkstyle:check && mvn spotbugs:check`（若插件已接入）

## Chunk 2: 用户权限与设备主数据

**Milestone Goal:** 建立认证鉴权、用户权限、通知能力、系统运维任务、设备分类和设备主数据，形成核心业务入口与主数据基础。

**Entry Condition:** Chunk 1 已通过，基础配置、安全基线、测试基座可用。

**Exit Condition:** 用户与权限、设备与分类模块可独立使用，通知能力仅支撑验证码、密码重置、冻结/解冻等用户侧通知，阶段 1-2 代码审查和测试通过。

### Task 6: 建立用户、角色、权限数据模型并扩展用户侧通知查询

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/User.java`
- Create: `src/main/java/com/jhu/device/management/entity/Role.java`
- Create: `src/main/java/com/jhu/device/management/entity/Permission.java`
- Create: `src/main/java/com/jhu/device/management/entity/RolePermission.java`
- Modify: `src/main/java/com/jhu/device/management/entity/NotificationRecord.java`
- Modify: `src/main/resources/sql/01_core_schema.sql`
- Create: `src/main/java/com/jhu/device/management/mapper/UserMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/RoleMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/PermissionMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/RolePermissionMapper.java`
- Modify: `src/main/java/com/jhu/device/management/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/UserMapper.xml`
- Create: `src/main/resources/mapper/RoleMapper.xml`
- Create: `src/main/resources/mapper/PermissionMapper.xml`
- Create: `src/main/resources/mapper/RolePermissionMapper.xml`
- Modify: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Test: `src/test/java/com/jhu/device/management/integration/mapper/UserMapperIntegrationTest.java`

- [ ] **Step 1: 先写 Mapper 集成测试，约束用户与角色查询关系**

```java
class UserMapperIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldLoadUserWithRoleIds() {
        User user = userMapper.selectById(1L);
        assertThat(user).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认实体和 Mapper 尚未建立**

Run: `mvn test -Dtest=UserMapperIntegrationTest`
Expected: FAIL，提示 Mapper Bean 或实体不存在

- [ ] **Step 3: 创建用户、角色、权限实体，并按用户侧通知场景扩展通知记录查询模型**

```java
public class User extends BaseEntity {
    private Long id;
    private String email;
    private String password;
    private String freezeStatus;
}
```

- [ ] **Step 4: 补齐基础 SQL 与 XML 映射，并明确通知底座已由阶段 0 建立，Chunk 2 仅扩展用户侧查询字段/索引/映射**

```xml
<select id="selectById" resultType="com.jhu.device.management.entity.User">
    SELECT * FROM user WHERE id = #{id}
</select>
```

- [ ] **Step 5: 回归 Mapper 集成测试**

Run: `mvn test -Dtest=UserMapperIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交用户域模型基线**

```bash
git add src/main/java/com/jhu/device/management/entity src/main/java/com/jhu/device/management/mapper src/main/resources/mapper src/test/java/com/jhu/device/management/integration/mapper/UserMapperIntegrationTest.java
git commit -m "feat(user): add user role permission domain model"
```

### Task 7: 实现认证链路（注册、登录、刷新、验证码、重置、登出）

**Files:**
- Create: `src/main/java/com/jhu/device/management/controller/AuthController.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/LoginRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/RegisterRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/RefreshTokenRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/SendVerificationCodeRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/ResetPasswordRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/LoginResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/VerificationCodeResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/auth/ResetTokenResponse.java`
- Create: `src/main/java/com/jhu/device/management/service/AuthService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/AuthServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/auth/AuthControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/auth/AuthFailureIntegrationTest.java`

- [ ] **Step 1: 先写登录接口集成测试，定义鉴权协议**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Test
    void shouldReturnTokensWhenLoginSucceeded() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" +
                    "\"email\":\"admin@example.com\"," +
                    "\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").exists());
    }
}
```

- [ ] **Step 2: 运行登录测试，确认认证链尚未实现**

Run: `mvn test -Dtest=AuthControllerIntegrationTest`
Expected: FAIL，提示接口不存在、服务 Bean 缺失或返回结构不匹配

- [ ] **Step 3: 实现 `AuthController`、`AuthService` 与 DTO**

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
```

- [ ] **Step 4: 基于阶段 0 预留的通知底座接入验证码、刷新 Token、重置密码与登出逻辑**

```java
public interface AuthService {
    LoginResponse login(LoginRequest request);
    void register(RegisterRequest request);
    LoginResponse refreshToken(RefreshTokenRequest request);
}
```

```text
职责边界：Task 7 只负责认证接口编排和对 `NotificationService` 的调用，不在本任务实现邮件发送、模板渲染、通知落库与失败重试；这些细节统一在 Task 8A 落地。
```

- [ ] **Step 5: 补齐验证码过期、刷新失败、登出后 Token 失效等失败路径测试**

Run: `mvn test -Dtest=AuthFailureIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 执行认证相关测试**

Run: `mvn test -Dtest=AuthServiceTest,AuthControllerIntegrationTest,AuthFailureIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: 提交认证模块**

```bash
git add src/main/java/com/jhu/device/management/controller/AuthController.java src/main/java/com/jhu/device/management/dto/auth src/main/java/com/jhu/device/management/service/AuthService.java src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java src/test/java/com/jhu/device/management/unit/service/AuthServiceTest.java src/test/java/com/jhu/device/management/integration/auth/AuthControllerIntegrationTest.java
git commit -m "feat(auth): implement authentication flow"
```

### Task 8: 实现用户管理、冻结解冻与权限分配

**Files:**
- Create: `src/main/java/com/jhu/device/management/controller/UserController.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/CreateUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UpdateUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/QueryUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UserResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/FreezeUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/UnfreezeUserRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/user/AssignPermissionRequest.java`
- Create: `src/main/java/com/jhu/device/management/service/UserService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/UserServiceImpl.java`
- Test: `src/test/java/com/jhu/device/management/integration/user/UserControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/user/UserPermissionGuardIntegrationTest.java`

- [ ] **Step 1: 先写用户冻结接口测试，约束管理员能力**

```java
class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldFreezeUser() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/freeze"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.freezeStatus").value("FROZEN"));
    }
}
```

- [ ] **Step 2: 运行用户管理测试，确认冻结与权限接口尚未存在**

Run: `mvn test -Dtest=UserControllerIntegrationTest`
Expected: FAIL，提示路由不存在或权限校验未接入

- [ ] **Step 3: 实现 `UserController` 和 `UserService` 管理接口**

```java
@PostMapping("/{id}/freeze")
public ApiResponse<UserResponse> freeze(@PathVariable Long id, @RequestBody FreezeUserRequest request) {
    return ApiResponse.success(userService.freezeUser(id, request));
}
```

- [ ] **Step 4: 补齐权限分配、冻结解冻与冻结用户访问拦截逻辑**

```java
public UserResponse freezeUser(Long id, FreezeUserRequest request) {
    return userService.freezeUser(id, request);
}
```

- [ ] **Step 5: 执行用户管理成功/失败路径测试**

Run: `mvn test -Dtest=UserControllerIntegrationTest,UserPermissionGuardIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交用户管理能力**

```bash
git add src/main/java/com/jhu/device/management/controller/UserController.java src/main/java/com/jhu/device/management/dto/user src/main/java/com/jhu/device/management/service/UserService.java src/main/java/com/jhu/device/management/service/impl/UserServiceImpl.java src/test/java/com/jhu/device/management/integration/user/UserControllerIntegrationTest.java src/test/java/com/jhu/device/management/integration/user/UserPermissionGuardIntegrationTest.java
git commit -m "feat(user): add admin management and freeze permission guards"
```

### Task 8A: 实现用户侧通知发送、落库与失败路径

**Files:**
- Create: `src/main/java/com/jhu/device/management/dto/notification/NotificationResponse.java`
- Modify: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/support/EmailSender.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/NotificationServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/notification/UserNotificationFailureIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/notification/FreezeNotificationIntegrationTest.java`

- [ ] **Step 1: 先写验证码、重置密码、冻结解冻通知测试**

```java
class NotificationServiceTest {

    @Test
    void shouldPersistNotificationRecordBeforeSend() {
        notificationService.sendVerifyCode("user@example.com", "123456");
        verify(notificationRecordMapper).insert(any());
    }
}
```

- [ ] **Step 2: 运行用户侧通知测试，确认通知能力尚未完整**

Run: `mvn test -Dtest=NotificationServiceTest,UserNotificationFailureIntegrationTest,FreezeNotificationIntegrationTest`
Expected: FAIL，提示通知服务、模板渲染或失败路径处理不存在

- [ ] **Step 3: 实现通知服务、邮件发送器接线和通知结果封装**

```java
public NotificationResponse sendVerifyCode(String email, String code) {
    return notificationService.sendVerifyCode(email, code);
}
```

- [ ] **Step 4: 补齐失败路径测试，覆盖发送失败、模板缺失和冻结通知**

Run: `mvn test -Dtest=NotificationServiceTest,UserNotificationFailureIntegrationTest,FreezeNotificationIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交用户侧通知能力**

```bash
git add src/main/java/com/jhu/device/management/dto/notification/NotificationResponse.java src/main/java/com/jhu/device/management/service/NotificationService.java src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java src/main/java/com/jhu/device/management/service/support/EmailSender.java src/test/java/com/jhu/device/management/unit/service/NotificationServiceTest.java src/test/java/com/jhu/device/management/integration/notification/UserNotificationFailureIntegrationTest.java src/test/java/com/jhu/device/management/integration/notification/FreezeNotificationIntegrationTest.java
git commit -m "feat(notification): add user notification workflow"
```

### Task 8B: 实现系统运维任务

**Files:**
- Create: `src/main/java/com/jhu/device/management/scheduler/system/TokenCleanupProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/system/SessionTimeoutProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/service/AuthService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java`
- Test: `src/test/java/com/jhu/device/management/integration/system/TokenCleanupProcessorIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/system/SessionTimeoutProcessorIntegrationTest.java`

- [ ] **Step 1: 先写 Token 清理与会话超时任务测试**

```java
class TokenCleanupProcessorIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldCleanupExpiredTokensIdempotently() {
    }
}
```

- [ ] **Step 2: 运行系统任务测试，确认调度任务尚未实现**

Run: `mvn test -Dtest=TokenCleanupProcessorIntegrationTest,SessionTimeoutProcessorIntegrationTest`
Expected: FAIL，提示处理器不存在或幂等断言未满足

- [ ] **Step 3: 实现 Token 清理和会话超时处理器**

```java
@Scheduled(cron = "0 0 3 * * ?")
public void cleanupExpiredTokens() {
    authService.cleanupExpiredTokens();
}
```

- [ ] **Step 4: 回归幂等与超时测试**

Run: `mvn test -Dtest=TokenCleanupProcessorIntegrationTest,SessionTimeoutProcessorIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交系统运维任务**

```bash
git add src/main/java/com/jhu/device/management/scheduler/system src/main/java/com/jhu/device/management/service/AuthService.java src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java src/test/java/com/jhu/device/management/integration/system/TokenCleanupProcessorIntegrationTest.java src/test/java/com/jhu/device/management/integration/system/SessionTimeoutProcessorIntegrationTest.java
git commit -m "feat(system): add token cleanup and session timeout processors"
```

### Task 9: 实现设备分类与设备 CRUD 主链路

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/DeviceCategory.java`
- Create: `src/main/java/com/jhu/device/management/entity/Device.java`
- Create: `src/main/java/com/jhu/device/management/controller/CategoryController.java`
- Create: `src/main/java/com/jhu/device/management/controller/DeviceController.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/CreateCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/UpdateCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/QueryCategoryRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/category/CategoryResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/CreateDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/UpdateDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/QueryDeviceRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/device/DeviceResponse.java`
- Create: `src/main/java/com/jhu/device/management/service/CategoryService.java`
- Create: `src/main/java/com/jhu/device/management/service/DeviceService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/CategoryServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceCategoryMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceMapper.java`
- Create: `src/main/resources/mapper/DeviceCategoryMapper.xml`
- Create: `src/main/resources/mapper/DeviceMapper.xml`
- Test: `src/test/java/com/jhu/device/management/integration/category/CategoryControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceControllerValidationIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DevicePermissionGuardIntegrationTest.java`

- [ ] **Step 1: 先写设备创建测试，锁定主数据接口行为**

```java
class DeviceControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldCreateDevice() throws Exception {
        mockMvc.perform(post("/api/devices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists());
    }
}
```

- [ ] **Step 2: 运行设备测试，确认设备接口尚未建立**

Run: `mvn test -Dtest=CategoryControllerIntegrationTest,DeviceControllerIntegrationTest,DeviceControllerValidationIntegrationTest,DevicePermissionGuardIntegrationTest`
Expected: FAIL，提示分类或设备控制器不存在

- [ ] **Step 3: 实现分类与设备实体、DTO、Service、Controller**

```java
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @PostMapping
    public ApiResponse<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
        return ApiResponse.success(deviceService.createDevice(request));
    }
}
```

- [ ] **Step 4: 补齐查询、更新、删除、分页和状态初始值逻辑**

```java
public interface DeviceService {
    DeviceResponse createDevice(CreateDeviceRequest request);
    DeviceResponse updateDevice(Long id, UpdateDeviceRequest request);
}
```

```text
状态边界：设备删除统一走 `DELETED` 终态；`BORROWED` 只允许由后续借还流程驱动，禁止在设备管理界面手工切换；`DISABLED` 和 `MAINTENANCE` 只允许管理员操作。
```

- [ ] **Step 5: 回归设备与分类成功/失败路径测试**

Run: `mvn test -Dtest=CategoryControllerIntegrationTest,DeviceControllerIntegrationTest,DeviceControllerValidationIntegrationTest,DevicePermissionGuardIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交设备主数据模块**

```bash
git add src/main/java/com/jhu/device/management/entity/Device.java src/main/java/com/jhu/device/management/entity/DeviceCategory.java src/main/java/com/jhu/device/management/controller/CategoryController.java src/main/java/com/jhu/device/management/controller/DeviceController.java src/main/java/com/jhu/device/management/dto/category src/main/java/com/jhu/device/management/dto/device src/main/java/com/jhu/device/management/service/CategoryService.java src/main/java/com/jhu/device/management/service/DeviceService.java src/main/java/com/jhu/device/management/service/impl/CategoryServiceImpl.java src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java src/test/java/com/jhu/device/management/integration/category/CategoryControllerIntegrationTest.java src/test/java/com/jhu/device/management/integration/device/DeviceControllerIntegrationTest.java
git commit -m "feat(device): add category and device crud"
```

### Task 10: 实现设备状态管理、图片上传与状态变更日志

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/DeviceStatusChangeLog.java`
- Create: `src/main/java/com/jhu/device/management/mapper/DeviceStatusChangeLogMapper.java`
- Create: `src/main/resources/mapper/DeviceStatusChangeLogMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/support/DeviceImageStorageSupport.java`
- Modify: `src/main/java/com/jhu/device/management/controller/DeviceController.java`
- Modify: `src/main/java/com/jhu/device/management/service/DeviceService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/DeviceServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/DeviceStateGuardTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/DeviceDeletedStateGuardTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceImageUploadIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceImageUploadFailureIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceStatusPermissionGuardIntegrationTest.java`

- [ ] **Step 1: 先写设备状态日志测试，锁定状态切换后的副作用**

```java
class DeviceServiceTest {

    @Test
    void shouldWriteStatusLogWhenDeviceStatusChanges() {
        deviceService.changeStatus(1L, "BORROWED");
        verify(deviceStatusChangeLogMapper).insert(any());
    }
}
```

- [ ] **Step 2: 运行测试，确认状态日志能力尚未建立**

Run: `mvn test -Dtest=DeviceServiceTest,DeviceImageUploadIntegrationTest`
Expected: FAIL，提示日志写入或上传接口不存在

- [ ] **Step 3: 实现状态日志实体、Mapper、状态变更接口与上传支撑组件**

```java
public interface DeviceImageStorageSupport {
    String store(MultipartFile file);
}
```

- [ ] **Step 4: 在 `DeviceServiceImpl` 中实现合法状态迁移校验、图片上传和状态日志写入**

```java
public void changeStatus(Long deviceId, String status) {
    validateStatusTransition(deviceId, status);
    deviceStatusChangeLogMapper.insert(buildStatusLog(deviceId, status));
}
```

```text
允许迁移矩阵：AVAILABLE -> MAINTENANCE/DISABLED；MAINTENANCE -> AVAILABLE/DISABLED；DISABLED -> AVAILABLE；BORROWED -> AVAILABLE（仅归还流程驱动）；DELETED 为终态，不允许恢复。
```

- [ ] **Step 5: 回归设备状态与上传成功/失败路径测试，明确断言删除终态、手工切换 `BORROWED` 被拒绝、非管理员切换受限状态被拒绝**

Run: `mvn test -Dtest=DeviceServiceTest,DeviceStateGuardTest,DeviceDeletedStateGuardTest,DeviceImageUploadIntegrationTest,DeviceImageUploadFailureIntegrationTest,DeviceStatusPermissionGuardIntegrationTest`
Expected: `BUILD SUCCESS`

```text
必须显式断言：非法状态迁移不会写入 `DeviceStatusChangeLog`，也不会把设备落到脏状态。
```

- [ ] **Step 6: 提交设备增强能力**

```bash
git add src/main/java/com/jhu/device/management/entity/DeviceStatusChangeLog.java src/main/java/com/jhu/device/management/mapper/DeviceStatusChangeLogMapper.java src/main/java/com/jhu/device/management/service/support/DeviceImageStorageSupport.java src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java src/test/java/com/jhu/device/management/unit/service/DeviceServiceTest.java src/test/java/com/jhu/device/management/integration/device/DeviceImageUploadIntegrationTest.java
git commit -m "feat(device): add image upload and status change logging"
```

### Task 11: 完成阶段 1-2 代码审查与测试门禁

**Files:**
- Modify: `src/main/java/com/jhu/device/management/controller/AuthController.java`
- Modify: `src/main/java/com/jhu/device/management/controller/UserController.java`
- Modify: `src/main/java/com/jhu/device/management/controller/CategoryController.java`
- Modify: `src/main/java/com/jhu/device/management/controller/DeviceController.java`
- Modify: `src/main/java/com/jhu/device/management/service/AuthService.java`
- Modify: `src/main/java/com/jhu/device/management/service/UserService.java`
- Modify: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/DeviceService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/DeviceServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/system/TokenCleanupProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/system/SessionTimeoutProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/entity/User.java`
- Modify: `src/main/java/com/jhu/device/management/entity/Device.java`
- Modify: `src/main/java/com/jhu/device/management/entity/DeviceCategory.java`
- Modify: `src/main/java/com/jhu/device/management/entity/DeviceStatusChangeLog.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/AuthServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/auth/AuthControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/auth/AuthFailureIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/user/UserControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/user/UserPermissionGuardIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/NotificationServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/system/TokenCleanupProcessorIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/system/SessionTimeoutProcessorIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceControllerValidationIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DevicePermissionGuardIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/DeviceDeletedStateGuardTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceImageUploadIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceImageUploadFailureIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/device/DeviceStatusPermissionGuardIntegrationTest.java`

- [ ] **Step 1: 汇总代码审查输入**

Run: `git diff -- src/main/java/com/jhu/device/management/controller src/main/java/com/jhu/device/management/service src/main/java/com/jhu/device/management/mapper src/main/java/com/jhu/device/management/entity`
Expected: 能完整看到认证、权限、通知、设备与分类的新增实现

- [ ] **Step 2: 按清单执行代码审查**

```text
检查分层是否清晰、DTO/VO 是否隔离、权限校验是否完整、冻结状态是否被拦截、验证码与刷新失败路径是否可控、通知记录是否可追踪、设备状态机是否合法、图片大小和路径限制是否来自配置。
```

**Stage 1 Sub Gate:**

- [ ] `mvn test -Dtest=UserMapperIntegrationTest,AuthServiceTest,AuthControllerIntegrationTest,AuthFailureIntegrationTest,UserControllerIntegrationTest,UserPermissionGuardIntegrationTest,NotificationServiceTest,UserNotificationFailureIntegrationTest,FreezeNotificationIntegrationTest,TokenCleanupProcessorIntegrationTest,SessionTimeoutProcessorIntegrationTest`
- [ ] 阶段 1 文档与配置已经同步，后续预约阶段依赖的用户、权限、通知、会话能力已就绪

**Stage 2 Sub Gate:**

- [ ] `mvn test -Dtest=CategoryControllerIntegrationTest,DeviceControllerIntegrationTest,DeviceControllerValidationIntegrationTest,DevicePermissionGuardIntegrationTest,DeviceServiceTest,DeviceStateGuardTest,DeviceDeletedStateGuardTest,DeviceImageUploadIntegrationTest,DeviceImageUploadFailureIntegrationTest,DeviceStatusPermissionGuardIntegrationTest`
- [ ] 已显式验证：分类层级非法请求被拒绝、`DELETED` 终态不可恢复、非管理员不能切换 `DISABLED/MAINTENANCE`、设备管理界面不能手工切到 `BORROWED`

- [ ] **Step 3: 修复审查问题并回归相关测试**

Run: `mvn test -Dtest=AuthServiceTest,AuthControllerIntegrationTest,AuthFailureIntegrationTest,UserControllerIntegrationTest,UserPermissionGuardIntegrationTest,NotificationServiceTest,UserNotificationFailureIntegrationTest,FreezeNotificationIntegrationTest,TokenCleanupProcessorIntegrationTest,SessionTimeoutProcessorIntegrationTest,CategoryControllerIntegrationTest,DeviceControllerIntegrationTest,DeviceControllerValidationIntegrationTest,DevicePermissionGuardIntegrationTest,DeviceServiceTest,DeviceStateGuardTest,DeviceDeletedStateGuardTest,DeviceImageUploadIntegrationTest,DeviceImageUploadFailureIntegrationTest,DeviceStatusPermissionGuardIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 执行阶段验收命令**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 执行完整校验与可选静态检查**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 如质量插件已接入，执行静态检查**

Run: `mvn checkstyle:check && mvn spotbugs:check`
Expected: `BUILD SUCCESS` 或因插件尚未接入而暂缓执行

- [ ] **Step 7: 提交阶段 1-2 里程碑**

```bash
git add src/main/java src/main/resources/mapper src/test/java
git commit -m "feat(stage1-2): deliver auth user and device modules"
```

**Chunk 2 Code Review Checklist:**

- [ ] 注册、登录、刷新、验证码、重置与登出接口是否齐全
- [ ] 用户冻结、解冻、权限分配是否只允许管理员访问
- [ ] 冻结用户、越权用户、无效 Token 是否都被拒绝
- [ ] `TokenCleanupProcessor`、`SessionTimeoutProcessor` 是否具备幂等性
- [ ] 设备分类与设备 CRUD 是否遵循 DTO/VO 隔离
- [ ] 设备状态变更接口是否具备状态机守卫
- [ ] 分类层级异常、已删除设备访问、冻结用户设备操作是否都被拦截
- [ ] 设备图片上传路径与大小限制是否来自配置
- [ ] 设备状态变更是否完整落日志

**Chunk 2 Test Gate:**

- [ ] `mvn test -Dtest=UserMapperIntegrationTest,AuthServiceTest,AuthControllerIntegrationTest,AuthFailureIntegrationTest,UserControllerIntegrationTest,UserPermissionGuardIntegrationTest,NotificationServiceTest,UserNotificationFailureIntegrationTest,FreezeNotificationIntegrationTest,TokenCleanupProcessorIntegrationTest,SessionTimeoutProcessorIntegrationTest,CategoryControllerIntegrationTest,DeviceControllerIntegrationTest,DeviceControllerValidationIntegrationTest,DevicePermissionGuardIntegrationTest,DeviceServiceTest,DeviceStateGuardTest,DeviceDeletedStateGuardTest,DeviceImageUploadIntegrationTest,DeviceImageUploadFailureIntegrationTest,DeviceStatusPermissionGuardIntegrationTest`
- [ ] `mvn clean test`
- [ ] `mvn clean verify`
- [ ] `mvn checkstyle:check && mvn spotbugs:check`（若插件已接入）

## Chunk 3: 预约、借还与逾期主流程

**Milestone Goal:** 建立预约申请、审核、签到、借用确认、归还确认、逾期治理的完整业务主链路，并让定时任务与通知能力可稳定运行。

**Entry Condition:** Chunk 2 已通过，用户权限和设备主数据可正常使用。

**Exit Condition:** 预约、借还、逾期业务链完整可用，关键状态流转和超时任务全部通过测试与审查。

### Task 12: 建立预约模型、校验器与冲突检测器

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/Reservation.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/CreateReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/UpdateReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/QueryReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ReservationResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/AuditReservationRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/CheckInRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ManualProcessRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/reservation/ConflictCheckResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/ReservationMapper.java`
- Create: `src/main/resources/mapper/ReservationMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/support/ReservationValidator.java`
- Create: `src/main/java/com/jhu/device/management/service/support/ConflictDetector.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/support/ReservationValidatorTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/support/ConflictDetectorTest.java`

- [ ] **Step 1: 先写预约时段冲突测试，锁定规则边界**

```java
class ConflictDetectorTest {

    @Test
    void shouldReportConflictWhenTimeRangesOverlap() {
        assertThat(conflictDetector.hasConflict(1L, startA, endA, startB, endB)).isTrue();
    }
}
```

- [ ] **Step 2: 运行校验与冲突测试，确认支撑组件尚未实现**

Run: `mvn test -Dtest=ReservationValidatorTest,ConflictDetectorTest`
Expected: FAIL，提示校验器或冲突检测器不存在

- [ ] **Step 3: 创建预约实体、DTO、Mapper 与支撑组件**

```java
public interface ReservationValidator {
    void validateCreateRequest(CreateReservationRequest request);
}
```

- [ ] **Step 4: 在 `ConflictDetector` 中封装时间冲突查询规则**

```java
public boolean hasConflict(Long deviceId, LocalDateTime start, LocalDateTime end) {
    return reservationMapper.countConflicts(deviceId, start, end) > 0;
}
```

- [ ] **Step 5: 回归预约支撑测试**

Run: `mvn test -Dtest=ReservationValidatorTest,ConflictDetectorTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交预约支撑层**

```bash
git add src/main/java/com/jhu/device/management/entity/Reservation.java src/main/java/com/jhu/device/management/dto/reservation src/main/java/com/jhu/device/management/mapper/ReservationMapper.java src/main/resources/mapper/ReservationMapper.xml src/main/java/com/jhu/device/management/service/support/ReservationValidator.java src/main/java/com/jhu/device/management/service/support/ConflictDetector.java src/test/java/com/jhu/device/management/unit/service/support/ReservationValidatorTest.java src/test/java/com/jhu/device/management/unit/service/support/ConflictDetectorTest.java
git commit -m "feat(reservation): add validator and conflict detector"
```

### Task 13: 实现预约申请、审核、签到、人工处理与预约定时任务

**Files:**
- Create: `src/main/java/com/jhu/device/management/controller/ReservationController.java`
- Create: `src/main/java/com/jhu/device/management/service/ReservationService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/ReservationServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAuditTimeoutReminder.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAutoExpireProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationCheckInTimeoutProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/BorrowConfirmTimeoutProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationUpcomingReminder.java`
- Create: `src/main/resources/templates/email/approval-passed.html`
- Create: `src/main/resources/templates/email/approval-rejected.html`
- Create: `src/main/resources/templates/email/checkin-timeout-warning.html`
- Create: `src/main/resources/templates/email/borrow-confirm-warning.html`
- Create: `src/main/resources/templates/email/reservation-cancelled.html`
- Create: `src/main/resources/templates/email/review-timeout-warning.html`
- Create: `src/main/resources/templates/email/reservation-reminder.html`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationStateGuardIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationCancellationIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationRejectionIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/scheduler/reservation/ReservationProcessorsIntegrationTest.java`

- [ ] **Step 1: 先写预约申请、审核拒绝与取消集成测试，定义主流程协议**

```java
class ReservationControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldCreateReservation() throws Exception {
        mockMvc.perform(post("/api/reservations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
```

- [ ] **Step 2: 运行预约集成测试，确认控制器与服务尚未实现**

Run: `mvn test -Dtest=ReservationControllerIntegrationTest,ReservationCancellationIntegrationTest,ReservationRejectionIntegrationTest,ReservationProcessorsIntegrationTest`
Expected: FAIL，提示预约路由和定时任务不存在

- [ ] **Step 3: 实现预约控制器、服务与审核/签到/人工处理接口**

```java
public interface ReservationService {
    ReservationResponse createReservation(CreateReservationRequest request);
    ReservationResponse auditReservation(Long id, AuditReservationRequest request);
    ReservationResponse checkIn(Long id, CheckInRequest request);
    ReservationResponse manualProcess(Long id, ManualProcessRequest request);
    ReservationResponse cancelReservation(Long id);
    void remindAuditTimeoutReservations();
    void processExpiredReservations();
    void processCheckInTimeoutReservations();
    void processBorrowConfirmTimeoutReservations();
    void remindUpcomingReservations();
}
```

- [ ] **Step 4: 实现 5 个预约定时任务，并接入通知服务**

```java
@Scheduled(cron = "0 15 * * * ?")
public void processExpiredReservations() {
    reservationService.processExpiredReservations();
}
```

- [ ] **Step 5: 回归预约成功/失败路径与定时任务测试**

Run: `mvn test -Dtest=ReservationControllerIntegrationTest,ReservationStateGuardIntegrationTest,ReservationCancellationIntegrationTest,ReservationRejectionIntegrationTest,ReservationProcessorsIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交预约主流程**

```bash
git add src/main/java/com/jhu/device/management/controller/ReservationController.java src/main/java/com/jhu/device/management/service/ReservationService.java src/main/java/com/jhu/device/management/service/impl/ReservationServiceImpl.java src/main/java/com/jhu/device/management/scheduler/reservation src/test/java/com/jhu/device/management/integration/reservation/ReservationControllerIntegrationTest.java src/test/java/com/jhu/device/management/integration/scheduler/reservation/ReservationProcessorsIntegrationTest.java
git commit -m "feat(reservation): implement reservation workflow and schedulers"
```

### Task 14: 实现借用确认、归还确认与借还记录查询

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/BorrowRecord.java`
- Create: `src/main/java/com/jhu/device/management/controller/BorrowController.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/ConfirmBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/ReturnBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/QueryBorrowRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/borrow/BorrowResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/BorrowRecordMapper.java`
- Create: `src/main/resources/mapper/BorrowRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/BorrowService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/BorrowServiceImpl.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/BorrowServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/borrow/BorrowControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/borrow/BorrowReturnGuardIntegrationTest.java`

- [ ] **Step 1: 先写借用确认测试，约束设备状态从预约到借出的切换**

```java
class BorrowControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldConfirmBorrow() throws Exception {
        mockMvc.perform(post("/api/borrow-records/1/borrow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.borrowStatus").value("BORROWED"));
    }
}
```

- [ ] **Step 2: 运行借还测试，确认借还链路尚未实现**

Run: `mvn test -Dtest=BorrowServiceTest,BorrowControllerIntegrationTest`
Expected: FAIL，提示借还控制器或服务不存在

- [ ] **Step 3: 实现借还实体、DTO、Mapper、Service 和 Controller**

```java
public interface BorrowService {
    BorrowResponse confirmBorrow(Long reservationId, ConfirmBorrowRequest request);
    BorrowResponse confirmReturn(Long borrowId, ReturnBorrowRequest request);
    PageResponse<BorrowResponse> queryBorrowRecords(QueryBorrowRequest request);
}
```

- [ ] **Step 4: 在借还服务中联动预约、设备与归还状态守卫**

```java
deviceService.changeStatus(deviceId, "BORROWED");
```

- [ ] **Step 5: 回归借还成功/失败路径测试**

Run: `mvn test -Dtest=BorrowServiceTest,BorrowControllerIntegrationTest,BorrowReturnGuardIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交借还模块**

```bash
git add src/main/java/com/jhu/device/management/entity/BorrowRecord.java src/main/java/com/jhu/device/management/controller/BorrowController.java src/main/java/com/jhu/device/management/dto/borrow src/main/java/com/jhu/device/management/mapper/BorrowRecordMapper.java src/main/resources/mapper/BorrowRecordMapper.xml src/main/java/com/jhu/device/management/service/BorrowService.java src/main/java/com/jhu/device/management/service/impl/BorrowServiceImpl.java src/test/java/com/jhu/device/management/unit/service/BorrowServiceTest.java src/test/java/com/jhu/device/management/integration/borrow/BorrowControllerIntegrationTest.java
git commit -m "feat(borrow): implement borrow and return workflow"
```

### Task 15: 实现逾期识别、限制治理、通知与逾期定时任务

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/OverdueRecord.java`
- Create: `src/main/java/com/jhu/device/management/controller/OverdueController.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/HandleOverdueRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/QueryOverdueRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/OverdueResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/overdue/OverdueStatisticsResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/OverdueRecordMapper.java`
- Create: `src/main/resources/mapper/OverdueRecordMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/OverdueService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/OverdueServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueAutoDetectProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueNotificationProcessor.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueRestrictionReleaseProcessor.java`
- Create: `src/main/resources/templates/email/overdue-warning.html`
- Create: `src/main/resources/templates/email/account-freeze-unfreeze.html`
- Test: `src/test/java/com/jhu/device/management/unit/service/OverdueServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/overdue/OverdueControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/overdue/OverdueStatisticsIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/scheduler/overdue/OverdueProcessorsIntegrationTest.java`

- [ ] **Step 1: 先写逾期识别测试，定义限制逻辑触发条件**

```java
class OverdueServiceTest {

    @Test
    void shouldCreateOverdueRecordWhenBorrowExpired() {
        overdueService.detectOverdueRecords();
        verify(overdueRecordMapper, atLeastOnce()).insert(any());
    }
}
```

- [ ] **Step 2: 运行逾期测试，确认治理链路尚未实现**

Run: `mvn test -Dtest=OverdueServiceTest,OverdueControllerIntegrationTest,OverdueStatisticsIntegrationTest,OverdueProcessorsIntegrationTest`
Expected: FAIL，提示逾期服务或控制器不存在

- [ ] **Step 3: 实现逾期实体、Service、Controller 与查询/处理接口**

```java
public interface OverdueService {
    void detectOverdueRecords();
    OverdueResponse handleOverdue(Long id, HandleOverdueRequest request);
    OverdueStatisticsResponse getStatistics(QueryOverdueRequest request);
    void notifyOverdueUsers();
    void releaseExpiredRestrictions();
}
```

- [ ] **Step 4: 实现逾期通知、自动检测和限制释放定时任务**

```java
@Scheduled(cron = "0 0 2 * * ?")
public void releaseRestrictions() {
    overdueService.releaseExpiredRestrictions();
}
```

- [ ] **Step 5: 回归逾期成功/失败路径与定时任务测试**

Run: `mvn test -Dtest=OverdueServiceTest,OverdueControllerIntegrationTest,OverdueStatisticsIntegrationTest,OverdueProcessorsIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交逾期治理模块**

```bash
git add src/main/java/com/jhu/device/management/entity/OverdueRecord.java src/main/java/com/jhu/device/management/controller/OverdueController.java src/main/java/com/jhu/device/management/dto/overdue src/main/java/com/jhu/device/management/mapper/OverdueRecordMapper.java src/main/resources/mapper/OverdueRecordMapper.xml src/main/java/com/jhu/device/management/service/OverdueService.java src/main/java/com/jhu/device/management/service/impl/OverdueServiceImpl.java src/main/java/com/jhu/device/management/scheduler/overdue src/test/java/com/jhu/device/management/unit/service/OverdueServiceTest.java src/test/java/com/jhu/device/management/integration/overdue/OverdueControllerIntegrationTest.java
git commit -m "feat(overdue): implement overdue detection and governance"
```

### Task 16: 完成阶段 3-5 代码审查与测试门禁

**Files:**
- Modify: `src/main/java/com/jhu/device/management/controller/ReservationController.java`
- Modify: `src/main/java/com/jhu/device/management/controller/BorrowController.java`
- Modify: `src/main/java/com/jhu/device/management/controller/OverdueController.java`
- Modify: `src/main/java/com/jhu/device/management/service/ReservationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/BorrowService.java`
- Modify: `src/main/java/com/jhu/device/management/service/OverdueService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/ReservationServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/BorrowServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/OverdueServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/service/NotificationService.java`
- Modify: `src/main/java/com/jhu/device/management/service/impl/NotificationServiceImpl.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAuditTimeoutReminder.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationAutoExpireProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationCheckInTimeoutProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/reservation/BorrowConfirmTimeoutProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/reservation/ReservationUpcomingReminder.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueAutoDetectProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueNotificationProcessor.java`
- Modify: `src/main/java/com/jhu/device/management/scheduler/overdue/OverdueRestrictionReleaseProcessor.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/support/ReservationValidatorTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/support/ConflictDetectorTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationStateGuardIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationCancellationIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/reservation/ReservationRejectionIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/borrow/BorrowControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/borrow/BorrowReturnGuardIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/overdue/OverdueControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/overdue/OverdueStatisticsIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/scheduler/overdue/OverdueProcessorsIntegrationTest.java`

- [ ] **Step 1: 汇总预约、借还、逾期审查范围**

Run: `git diff -- src/main/java/com/jhu/device/management/controller/ReservationController.java src/main/java/com/jhu/device/management/controller/BorrowController.java src/main/java/com/jhu/device/management/controller/OverdueController.java src/main/java/com/jhu/device/management/service src/main/java/com/jhu/device/management/scheduler`
Expected: 能完整看到主流程状态流转、校验器、定时任务和通知集成

- [ ] **Step 2: 按清单执行代码审查**

```text
检查冲突检测是否准确、审核/签到/人工处理状态流转是否正确、借还是否联动预约与设备状态、归还后状态是否正确终止逾期链、逾期检测和释放是否可重复执行、定时任务是否幂等、通知模板和记录是否有落点。
```

- [ ] **Step 3: 回归主流程测试套件**

Run: `mvn test -Dtest=ReservationValidatorTest,ConflictDetectorTest,ReservationControllerIntegrationTest,ReservationStateGuardIntegrationTest,ReservationCancellationIntegrationTest,ReservationRejectionIntegrationTest,ReservationProcessorsIntegrationTest,BorrowServiceTest,BorrowControllerIntegrationTest,BorrowReturnGuardIntegrationTest,OverdueServiceTest,OverdueControllerIntegrationTest,OverdueStatisticsIntegrationTest,OverdueProcessorsIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 执行阶段验收命令**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 执行完整校验**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 如质量插件已接入，执行静态检查**

Run: `mvn checkstyle:check && mvn spotbugs:check`
Expected: `BUILD SUCCESS` 或因插件尚未接入而暂缓执行

- [ ] **Step 7: 提交阶段 3-5 里程碑**

```bash
git add src/main/java src/main/resources/mapper src/test/java
git commit -m "feat(stage3-5): deliver reservation borrow and overdue workflow"
```

**Chunk 3 Code Review Checklist:**

- [ ] 预约创建、审核、签到、人工处理接口是否与 `AGENTS.md` 对齐
- [ ] 预约取消、审核拒绝、签到超时、借用确认超时等失败路径是否都被显式验证
- [ ] 冲突检测是否覆盖重叠时间段与边界时间点
- [ ] 借用确认、归还确认和借还历史查询是否严格校验预约/借还状态
- [ ] 逾期记录、限制状态、通知状态与解除限制是否可追踪
- [ ] 逾期统计口径是否可查询且与业务状态一致
- [ ] 通知类型与模板是否覆盖审核通过、审核拒绝、预约取消、签到超时、借用确认超时、逾期提醒
- [ ] 预约与逾期定时任务是否具备幂等和容错
- [ ] 主流程异常场景是否都有统一异常返回

**Chunk 3 Test Gate:**

- [ ] `mvn test -Dtest=ReservationValidatorTest,ConflictDetectorTest,ReservationControllerIntegrationTest,ReservationStateGuardIntegrationTest,ReservationCancellationIntegrationTest,ReservationRejectionIntegrationTest,ReservationProcessorsIntegrationTest,BorrowServiceTest,BorrowControllerIntegrationTest,BorrowReturnGuardIntegrationTest,OverdueServiceTest,OverdueControllerIntegrationTest,OverdueStatisticsIntegrationTest,OverdueProcessorsIntegrationTest`
- [ ] `mvn clean test`
- [ ] `mvn clean verify`
- [ ] `mvn checkstyle:check && mvn spotbugs:check`（若插件已接入）

## Chunk 4: AI、统计、联调与发布准备

**Milestone Goal:** 在核心业务稳定的前提下补齐 AI、统计分析、日志与部署资产，并完成跨模块联调、质量加固和发布前验收。

**Entry Condition:** Chunk 3 已通过，核心业务数据链路稳定。

**Exit Condition:** AI、统计、联调和发布准备全部完成，系统达到可交付状态。

### Task 17: 实现 AI 对话、意图识别、槽位提取与提示模板管理

**Files:**
- Create: `src/main/java/com/jhu/device/management/config/ai/AiConfig.java`
- Create: `src/main/java/com/jhu/device/management/config/ai/PromptConfig.java`
- Create: `src/main/java/com/jhu/device/management/entity/ChatHistory.java`
- Create: `src/main/java/com/jhu/device/management/entity/PromptTemplate.java`
- Create: `src/main/java/com/jhu/device/management/controller/AiController.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiChatRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiChatResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiIntentResult.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/AiSlotInfo.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/CreatePromptTemplateRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/UpdatePromptTemplateRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/ai/PromptTemplateResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/ChatHistoryMapper.java`
- Create: `src/main/java/com/jhu/device/management/mapper/PromptTemplateMapper.java`
- Create: `src/main/resources/mapper/ChatHistoryMapper.xml`
- Create: `src/main/resources/mapper/PromptTemplateMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/AiService.java`
- Create: `src/main/java/com/jhu/device/management/service/PromptTemplateService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/AiServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/PromptTemplateServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/service/support/IntentRecognizer.java`
- Create: `src/main/java/com/jhu/device/management/service/support/SlotExtractor.java`
- Create: `src/main/java/com/jhu/device/management/service/support/PromptEngine.java`
- Create: `src/main/java/com/jhu/device/management/service/support/LlmClient.java`
- Create: `src/main/java/com/jhu/device/management/service/support/LlmClientImpl.java`
- Create: `src/main/resources/templates/ai/device-query.txt`
- Create: `src/main/resources/templates/ai/reservation-audit.txt`
- Test: `src/test/java/com/jhu/device/management/unit/service/ai/IntentRecognizerTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/ai/SlotExtractorTest.java`
- Test: `src/test/java/com/jhu/device/management/unit/service/ai/PromptTemplateServiceTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/ai/AiFallbackIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/ai/AiControllerIntegrationTest.java`

- [ ] **Step 1: 先写意图识别测试，定义 AI 服务输入输出边界**

```java
class IntentRecognizerTest {

    @Test
    void shouldRecognizeReservationIntent() {
        assertThat(intentRecognizer.recognize("我想预约明天下午的示波器").intent()).isEqualTo("CREATE_RESERVATION");
    }
}
```

- [ ] **Step 2: 运行 AI 测试，确认识别与控制器尚未存在**

Run: `mvn test -Dtest=IntentRecognizerTest,SlotExtractorTest,PromptTemplateServiceTest,AiControllerIntegrationTest,AiFallbackIntegrationTest`
Expected: FAIL，提示 AI 支撑组件和控制器不存在

- [ ] **Step 3: 实现 AI 配置、Service、Controller 与持久化对象**

```java
public interface AiService {
    AiChatResponse chat(AiChatRequest request);
}
```

- [ ] **Step 4: 接入意图识别、槽位提取、Prompt 模板与 LLM 客户端**

```java
public interface LlmClient {
    String chat(String prompt);
}
```

- [ ] **Step 4A: 明确 AI 降级协议并固化到失败测试**

```text
降级场景至少包括：LLM 超时、供应商 5xx、Prompt 模板缺失或解析失败。统一返回约定：`AiChatResponse` 标记 `fallback=true`，返回可读提示语，保留基础意图结果，记录聊天历史与错误日志，但不直接修改业务数据。
```

- [ ] **Step 5: 回归 AI 成功链路、模板管理和异常降级测试**

Run: `mvn test -Dtest=IntentRecognizerTest,SlotExtractorTest,PromptTemplateServiceTest,AiControllerIntegrationTest,AiFallbackIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交 AI 模块**

```bash
git add src/main/java/com/jhu/device/management/config/ai src/main/java/com/jhu/device/management/entity/ChatHistory.java src/main/java/com/jhu/device/management/entity/PromptTemplate.java src/main/java/com/jhu/device/management/controller/AiController.java src/main/java/com/jhu/device/management/dto/ai src/main/java/com/jhu/device/management/mapper/ChatHistoryMapper.java src/main/java/com/jhu/device/management/mapper/PromptTemplateMapper.java src/main/resources/mapper/ChatHistoryMapper.xml src/main/resources/mapper/PromptTemplateMapper.xml src/main/java/com/jhu/device/management/service/AiService.java src/main/java/com/jhu/device/management/service/PromptTemplateService.java src/main/java/com/jhu/device/management/service/impl/AiServiceImpl.java src/main/java/com/jhu/device/management/service/impl/PromptTemplateServiceImpl.java src/main/java/com/jhu/device/management/service/support/IntentRecognizer.java src/main/java/com/jhu/device/management/service/support/SlotExtractor.java src/main/java/com/jhu/device/management/service/support/PromptEngine.java src/main/java/com/jhu/device/management/service/support/LlmClient.java src/main/java/com/jhu/device/management/service/support/LlmClientImpl.java src/main/resources/templates/ai/device-query.txt src/test/java/com/jhu/device/management/unit/service/ai src/test/java/com/jhu/device/management/integration/ai/AiControllerIntegrationTest.java
git commit -m "feat(ai): implement chat intent and prompt template workflow"
```

### Task 18: 实现统计概览、预聚合与统计接口

**Files:**
- Create: `src/main/java/com/jhu/device/management/entity/StatisticsDaily.java`
- Create: `src/main/java/com/jhu/device/management/controller/StatisticsController.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/QueryStatisticsRequest.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/OverviewResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/DeviceUsageResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/BorrowStatsResponse.java`
- Create: `src/main/java/com/jhu/device/management/dto/statistics/HotTimeSlotResponse.java`
- Create: `src/main/java/com/jhu/device/management/mapper/StatisticsDailyMapper.java`
- Create: `src/main/resources/mapper/StatisticsDailyMapper.xml`
- Create: `src/main/java/com/jhu/device/management/service/StatisticsService.java`
- Create: `src/main/java/com/jhu/device/management/service/impl/StatisticsServiceImpl.java`
- Create: `src/main/java/com/jhu/device/management/scheduler/statistics/StatisticsAggregationProcessor.java`
- Test: `src/test/java/com/jhu/device/management/integration/statistics/StatisticsControllerIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/scheduler/statistics/StatisticsAggregationProcessorIntegrationTest.java`
- Test: `src/test/java/com/jhu/device/management/integration/statistics/StatisticsOutputIntegrationTest.java`

- [ ] **Step 1: 先写统计概览测试，定义返回结构与口径**

```java
class StatisticsControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldReturnOverview() throws Exception {
        mockMvc.perform(get("/api/statistics/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalDevices").exists());
    }
}
```

```text
统计口径约定：概览基于 `device`、`reservation`、`borrow_record`、`overdue_record`；设备利用率只统计非 `DELETED` 设备；借用统计排除取消和过期预约；热门时段按有效借用/预约时间窗口聚合。
```

- [ ] **Step 2: 运行统计测试，确认接口和聚合任务尚未实现**

Run: `mvn test -Dtest=StatisticsControllerIntegrationTest,StatisticsOutputIntegrationTest,StatisticsAggregationProcessorIntegrationTest`
Expected: FAIL，提示统计控制器或预聚合任务不存在

- [ ] **Step 3: 实现统计实体、Mapper、Service 与 Controller**

```java
public interface StatisticsService {
    OverviewResponse getOverview(QueryStatisticsRequest request);
}
```

- [ ] **Step 4: 实现日聚合任务和热门时段、设备利用率统计逻辑**

```java
@Scheduled(cron = "0 30 2 * * ?")
public void aggregateDailyStatistics() {
    statisticsService.aggregateDailyStatistics();
}
```

```text
幂等策略：`statistics_daily` 以统计日期 + 统计维度作为唯一键，重复执行同一天聚合时走覆盖更新而不是新增插入，并用测试断言重复执行后结果不重复累加。
```

- [ ] **Step 5: 回归统计口径、重复聚合幂等与异常路径测试**

Run: `mvn test -Dtest=StatisticsControllerIntegrationTest,StatisticsOutputIntegrationTest,StatisticsAggregationProcessorIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 提交统计模块**

```bash
git add src/main/java/com/jhu/device/management/entity/StatisticsDaily.java src/main/java/com/jhu/device/management/controller/StatisticsController.java src/main/java/com/jhu/device/management/dto/statistics src/main/java/com/jhu/device/management/mapper/StatisticsDailyMapper.java src/main/resources/mapper/StatisticsDailyMapper.xml src/main/java/com/jhu/device/management/service/StatisticsService.java src/main/java/com/jhu/device/management/service/impl/StatisticsServiceImpl.java src/main/java/com/jhu/device/management/scheduler/statistics/StatisticsAggregationProcessor.java src/test/java/com/jhu/device/management/integration/statistics/StatisticsControllerIntegrationTest.java src/test/java/com/jhu/device/management/integration/scheduler/statistics/StatisticsAggregationProcessorIntegrationTest.java
git commit -m "feat(statistics): add overview metrics and aggregation job"
```

### Task 19: 完成联调、日志、部署资产与发布准备

**Files:**
- Create: `src/main/resources/templates/email/verify-code.html`
- Modify: `src/main/resources/templates/email/reservation-reminder.html`
- Modify: `src/main/resources/templates/email/overdue-warning.html`
- Modify: `src/main/resources/templates/email/account-freeze-unfreeze.html`
- Create: `src/main/resources/logback-spring.xml`
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `README.md`
- Modify: `src/main/resources/application-prod.yml`
- Test: `src/test/java/com/jhu/device/management/integration/system/EndToEndSmokeIntegrationTest.java`

- [ ] **Step 1: 先写端到端冒烟测试，锁定核心接口联通性**

```java
class EndToEndSmokeIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldCompleteLoginToReservationFlow() {
        // 登录 -> 查询设备 -> 创建预约 -> AI 对话 -> 查看统计概览
    }
}
```

- [ ] **Step 2: 运行冒烟测试，确认联调和部署资产尚未补齐**

Run: `mvn test -Dtest=EndToEndSmokeIntegrationTest`
Expected: FAIL，提示跨模块链路、模板或配置缺失

- [ ] **Step 3: 补齐邮件模板、日志配置、Docker 资产和 README**

```dockerfile
FROM eclipse-temurin:21-jre
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- [ ] **Step 4: 验证 README、模板渲染、日志输出与容器配置**

Run: `mvn test -Dtest=EndToEndSmokeIntegrationTest`
Expected: `BUILD SUCCESS`，且 README、邮件模板、日志配置、容器启动步骤均有对应说明

- [ ] **Step 4A: 执行发布资产显式校验命令**

Run: `mvn clean package && docker build -t device-management-backend:local . && docker compose config`
Expected: 打包成功、镜像可构建、`docker-compose.yml` 解析通过，且 `application-prod.yml` 所需环境变量占位符已核对完整

- [ ] **Step 5: 完成跨模块联调并修复高风险缺陷**

Run: `mvn test -Dtest=EndToEndSmokeIntegrationTest`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 执行发布前完整验证**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: 提交发布准备资产**

```bash
git add src/main/resources/templates src/main/resources/logback-spring.xml src/main/resources/application-prod.yml Dockerfile docker-compose.yml README.md src/test/java/com/jhu/device/management/integration/system/EndToEndSmokeIntegrationTest.java
git commit -m "chore(release): add deployment assets and integration docs"
```

### Task 20: 完成最终代码审查、质量门禁与交付清单

**Files:**
- Modify: 当前计划涉及的所有实现文件
- Test: 全量测试集

- [ ] **Step 1: 汇总最终审查清单并执行阶段总审查**

```text
检查模块边界、接口路径、权限控制、状态机、定时任务幂等性、异常一致性、AI 降级、统计口径、配置安全性、日志完整性、部署文档完备性。
```

```text
最终阻塞项：AI 降级必须可用；统计重复执行结果不变；生产配置占位符齐全；容器构建成功；端到端冒烟通过；模板渲染与日志配置可验证。
```

- [ ] **Step 2: 执行核心测试集**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 执行完整校验**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 如果已接入质量插件，再执行静态检查**

Run: `mvn checkstyle:check && mvn spotbugs:check`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 若任一阻塞项失败，回退到对应 Chunk 修复并重新执行完整验证**

```text
阻塞项包括：权限绕过、状态流转错误、定时任务重复副作用、AI 无降级、统计口径错误、部署配置缺失。
```

- [ ] **Step 6: 形成最终交付物清单**

```text
交付物应至少包含：源码、Mapper XML、配置文件、测试用例、模板文件、日志配置、README、Docker 资产、发布验证结果记录。
```

- [ ] **Step 7: 记录最终里程碑提交**

```bash
git add src/main/java src/main/resources src/test/java Dockerfile docker-compose.yml README.md pom.xml docs/superpowers/specs/2026-03-11-device-management-implementation-roadmap-design.md docs/superpowers/plans/2026-03-11-device-management-backend-implementation-plan.md
git commit -m "feat: deliver device management backend implementation baseline"
```

**Chunk 4 Code Review Checklist:**

- [ ] AI 对话是否与核心业务服务解耦，异常时能否降级
- [ ] Prompt 模板、意图识别、槽位提取是否可单独测试
- [ ] 统计口径是否基于已稳定的业务数据模型，且重复聚合不会重复计数
- [ ] 预聚合任务、日志配置、Docker 资产是否满足发布前需要
- [ ] README 是否覆盖启动、配置、测试、部署步骤，`application-prod.yml` 是否与部署说明一致
- [ ] 模板文件是否覆盖验证码、预约提醒、逾期提醒等必要通知场景
- [ ] 最终交付物是否包含所有必需模块与文档

**Chunk 4 Test Gate:**

- [ ] `mvn test -Dtest=IntentRecognizerTest,SlotExtractorTest,PromptTemplateServiceTest,AiControllerIntegrationTest,AiFallbackIntegrationTest,StatisticsControllerIntegrationTest,StatisticsOutputIntegrationTest,StatisticsAggregationProcessorIntegrationTest,EndToEndSmokeIntegrationTest`
- [ ] `mvn clean test`
- [ ] `mvn clean verify`
- [ ] `mvn checkstyle:check && mvn spotbugs:check`（若插件已接入）

## 结尾说明

- 该计划是“总实施计划文档”，执行时只允许按 Chunk 顺序推进，不建议跨 Chunk 并行开发
- 如果某一 Chunk 范围超出单次会话可承载范围，应在执行前把当前 Chunk 再拆成子计划，但不得改变阶段顺序
- 每完成一个 Task，都要更新对应测试和实现，不要把测试堆到 Chunk 末尾再统一补写
- 每完成一个 Chunk，都必须先完成代码审查和测试门禁，再继续下一个 Chunk
- 如需真正开始编码，实现会话应先读取 `docs/superpowers/specs/2026-03-11-device-management-implementation-roadmap-design.md` 和本计划文档
