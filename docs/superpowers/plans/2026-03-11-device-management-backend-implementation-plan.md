# Device Management Backend Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于当前最小化 Spring Boot 仓库，按系统设计、前后端目录设计与 `device_management.sql` 真相源，分阶段交付可联调、可测试、可验收的设备管理后端。

**Architecture:** 保持当前 `com.jhun.backend` 包根，在该根下按 `config -> common -> dto -> entity -> mapper -> service -> controller -> scheduler -> util` 扩展工程结构。优先对齐 Spring Boot 4.x、真实 SQL、统一响应/异常、安全与测试基座，再按“认证权限与通知底座 -> 设备主数据 -> 预约双审批与批量预约 -> 借还逾期 -> AI/统计/发布”的顺序实现业务闭环。

**Tech Stack:** Java 21, Maven 3.x, Spring Boot 4.x, Spring Security 6.x, MyBatis-Plus, MySQL 8.x, Redis, JWT, Spring AI, JUnit 5, MockMvc, Lombok

---

## 0. 当前上下文与真相源

- 当前仓库仅包含：
  - `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`
  - `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`
  - `src/main/resources/application.properties`
  - 最小化 `pom.xml`
- 当前包根为 `com.jhun.backend`，后续计划**不迁移**到 `com.jhu.device.management`
- 当前 `pom.xml` 使用 Spring Boot `4.0.3` 初始化骨架，后续实现以 Spring Boot `4.x` 作为工程基线继续扩展
- 业务与数据真相源以 `device_management.sql` 为准，关键口径包括：
  - 主键统一 `String` UUID
  - 角色固定为 `USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN`
  - 审批模式固定为 `DEVICE_ONLY` / `DEVICE_THEN_SYSTEM`
  - 存在正式表 `reservation_batch`
  - `notification_record` 已包含 `channel`、`read_flag`、`read_at`、`template_vars`、`retry_count`、`related_id`、`related_type`
  - AI 意图固定为 `RESERVE / QUERY / CANCEL / HELP / UNKNOWN`
  - 统计模型固定为 `stat_type` / `granularity` / `subject_type` / `subject_value`
- 前端目录稿已经明确存在 `notifications` 模块与 `markAsRead` / `unreadCount` 承载，因此通知已读不是待确认需求，而是实施项
- `DialogConfig` 当前缺少稳定实体、SQL 和前端承载，本计划不直接落代码

## 1. 执行总原则

- 所有实现都以 TDD 为默认路径：先写失败测试，再写最小实现，再回归验证
- 每个 Chunk 完成前都要执行阶段性代码审查与验证命令
- 严禁在代码、测试、示例请求中继续使用 `Long` / `1L` / `BIGINT` 作为主键假设
- 所有新增代码默认继续落在 `com.jhun.backend` 下，不主动迁移包根
- 若系统设计旧文档与 SQL 新口径冲突，优先修改文档与测试，禁止为兼容旧口径而降级实现
- `dev/test` 可以通过 profile 专用 seed 或 bootstrap runner 提供管理员测试账号；生产库不在 SQL 种子中硬编码默认管理员账号
- 每次改动 schema、接口、枚举、定时任务时，同步更新相关文档
- 除非用户明确要求，否则计划只写出建议提交命令，不在当前会话实际提交

## 2. 目标文件地图

### 2.1 工程基线与通用能力

- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`
- Modify: `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `src/main/resources/application-prod.yml`
- Create: `src/main/resources/sql/01_schema.sql`
- Create: `src/main/resources/sql/02_seed_roles.sql`
- Create: `src/main/resources/sql/03_seed_permissions.sql`
- Create: `src/main/resources/sql/04_seed_role_permissions.sql`
- Create: `src/main/java/com/jhun/backend/config/**`
- Create: `src/main/java/com/jhun/backend/common/**`
- Create: `src/main/java/com/jhun/backend/util/**`
- Create: `src/test/java/com/jhun/backend/unit/config/**`
- Create: `src/test/java/com/jhun/backend/unit/common/**`
- Create: `src/test/java/com/jhun/backend/integration/support/**`
- Create: `src/test/java/com/jhun/backend/integration/security/**`

### 2.2 身份、用户、角色与通知底座

- Create: `src/main/java/com/jhun/backend/entity/User.java`
- Create: `src/main/java/com/jhun/backend/entity/Role.java`
- Create: `src/main/java/com/jhun/backend/entity/Permission.java`
- Create: `src/main/java/com/jhun/backend/entity/RolePermission.java`
- Create: `src/main/java/com/jhun/backend/entity/PasswordHistory.java`
- Create: `src/main/java/com/jhun/backend/entity/NotificationRecord.java`
- Create: `src/main/java/com/jhun/backend/mapper/UserMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/RoleMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PermissionMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/RolePermissionMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PasswordHistoryMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/UserMapper.xml`
- Create: `src/main/resources/mapper/RoleMapper.xml`
- Create: `src/main/resources/mapper/PermissionMapper.xml`
- Create: `src/main/resources/mapper/RolePermissionMapper.xml`
- Create: `src/main/resources/mapper/PasswordHistoryMapper.xml`
- Create: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Create: `src/main/java/com/jhun/backend/dto/auth/**`
- Create: `src/main/java/com/jhun/backend/dto/user/**`
- Create: `src/main/java/com/jhun/backend/dto/role/**`
- Create: `src/main/java/com/jhun/backend/dto/notification/**`
- Create: `src/main/java/com/jhun/backend/service/AuthService.java`
- Create: `src/main/java/com/jhun/backend/service/UserService.java`
- Create: `src/main/java/com/jhun/backend/service/RoleService.java`
- Create: `src/main/java/com/jhun/backend/service/NotificationService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/RoleServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/notification/EmailSender.java`
- Create: `src/main/java/com/jhun/backend/service/support/notification/SmsSender.java`
- Create: `src/main/java/com/jhun/backend/service/support/notification/NotificationTemplateAssembler.java`
- Create: `src/main/java/com/jhun/backend/controller/AuthController.java`
- Create: `src/main/java/com/jhun/backend/controller/UserController.java`
- Create: `src/main/java/com/jhun/backend/controller/RoleController.java`
- Create: `src/main/java/com/jhun/backend/controller/NotificationController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/system/TokenCleanupProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/system/SessionTimeoutProcessor.java`
- Create: `src/main/resources/templates/email/**`
- Create: `src/test/java/com/jhun/backend/unit/service/AuthServiceTest.java`
- Create: `src/test/java/com/jhun/backend/integration/auth/AuthControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/user/UserControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/role/RoleControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/notification/NotificationControllerIntegrationTest.java`

### 2.3 设备与分类主数据

- Create: `src/main/java/com/jhun/backend/entity/DeviceCategory.java`
- Create: `src/main/java/com/jhun/backend/entity/Device.java`
- Create: `src/main/java/com/jhun/backend/entity/DeviceStatusLog.java`
- Create: `src/main/java/com/jhun/backend/dto/category/**`
- Create: `src/main/java/com/jhun/backend/dto/device/**`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceCategoryMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceStatusLogMapper.java`
- Create: `src/main/resources/mapper/DeviceCategoryMapper.xml`
- Create: `src/main/resources/mapper/DeviceMapper.xml`
- Create: `src/main/resources/mapper/DeviceStatusLogMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/CategoryService.java`
- Create: `src/main/java/com/jhun/backend/service/DeviceService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/CategoryServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/DeviceServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/device/DeviceImageStorageSupport.java`
- Create: `src/main/java/com/jhun/backend/controller/DeviceCategoryController.java`
- Create: `src/main/java/com/jhun/backend/controller/DeviceController.java`
- Create: `src/test/java/com/jhun/backend/unit/service/DeviceServiceTest.java`
- Create: `src/test/java/com/jhun/backend/integration/category/CategoryControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/device/DeviceControllerIntegrationTest.java`

### 2.4 预约、审批、代预约与批量预约

- Create: `src/main/java/com/jhun/backend/entity/Reservation.java`
- Create: `src/main/java/com/jhun/backend/entity/ReservationBatch.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/**`
- Create: `src/main/java/com/jhun/backend/dto/reservationbatch/**`
- Create: `src/main/java/com/jhun/backend/mapper/ReservationMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/ReservationBatchMapper.java`
- Create: `src/main/resources/mapper/ReservationMapper.xml`
- Create: `src/main/resources/mapper/ReservationBatchMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/ReservationService.java`
- Create: `src/main/java/com/jhun/backend/service/ReservationBatchService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/ReservationServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/ReservationBatchServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/reservation/ReservationValidator.java`
- Create: `src/main/java/com/jhun/backend/service/support/reservation/ConflictDetector.java`
- Create: `src/main/java/com/jhun/backend/controller/ReservationController.java`
- Create: `src/main/java/com/jhun/backend/controller/ReservationBatchController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationAuditTimeoutReminder.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationAutoExpireProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationCheckInTimeoutProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/BorrowConfirmTimeoutProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationUpcomingReminder.java`
- Create: `src/test/java/com/jhun/backend/unit/service/support/ReservationValidatorTest.java`
- Create: `src/test/java/com/jhun/backend/unit/service/support/ConflictDetectorTest.java`
- Create: `src/test/java/com/jhun/backend/unit/service/ReservationServiceTest.java`
- Create: `src/test/java/com/jhun/backend/integration/reservation/ReservationControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/reservation/ReservationConflictConcurrencyIT.java`
- Create: `src/test/java/com/jhun/backend/integration/reservationbatch/ReservationBatchControllerIntegrationTest.java`

### 2.5 借还、逾期与通知联动

- Create: `src/main/java/com/jhun/backend/entity/BorrowRecord.java`
- Create: `src/main/java/com/jhun/backend/entity/OverdueRecord.java`
- Create: `src/main/java/com/jhun/backend/dto/borrow/**`
- Create: `src/main/java/com/jhun/backend/dto/overdue/**`
- Create: `src/main/java/com/jhun/backend/mapper/BorrowRecordMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/OverdueRecordMapper.java`
- Create: `src/main/resources/mapper/BorrowRecordMapper.xml`
- Create: `src/main/resources/mapper/OverdueRecordMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/BorrowService.java`
- Create: `src/main/java/com/jhun/backend/service/OverdueService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/BorrowServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/OverdueServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/BorrowController.java`
- Create: `src/main/java/com/jhun/backend/controller/OverdueController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueAutoDetectProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueNotificationProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueRestrictionReleaseProcessor.java`
- Create: `src/test/java/com/jhun/backend/integration/borrow/BorrowControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/overdue/OverdueControllerIntegrationTest.java`

### 2.6 AI、Prompt、统计与发布资产

- Create: `src/main/java/com/jhun/backend/entity/ChatHistory.java`
- Create: `src/main/java/com/jhun/backend/entity/PromptTemplate.java`
- Create: `src/main/java/com/jhun/backend/entity/StatisticsDaily.java`
- Create: `src/main/java/com/jhun/backend/dto/ai/**`
- Create: `src/main/java/com/jhun/backend/dto/statistics/**`
- Create: `src/main/java/com/jhun/backend/mapper/ChatHistoryMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PromptTemplateMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/StatisticsDailyMapper.java`
- Create: `src/main/resources/mapper/ChatHistoryMapper.xml`
- Create: `src/main/resources/mapper/PromptTemplateMapper.xml`
- Create: `src/main/resources/mapper/StatisticsDailyMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/AiService.java`
- Create: `src/main/java/com/jhun/backend/service/PromptTemplateService.java`
- Create: `src/main/java/com/jhun/backend/service/StatisticsService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/AiServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/PromptTemplateServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/StatisticsServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/IntentRecognizer.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/SlotExtractor.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/PromptEngine.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/LlmClient.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/LlmClientImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/AiController.java`
- Create: `src/main/java/com/jhun/backend/controller/PromptTemplateController.java`
- Create: `src/main/java/com/jhun/backend/controller/StatisticsController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/statistics/StatisticsAggregationProcessor.java`
- Create: `src/main/resources/templates/ai/**`
- Create: `src/main/resources/logback-spring.xml`
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `README.md`
- Create: `src/test/java/com/jhun/backend/integration/ai/**`
- Create: `src/test/java/com/jhun/backend/integration/statistics/**`
- Create: `src/test/java/com/jhun/backend/integration/system/EndToEndSmokeIntegrationTest.java`

## Chunk 1: 工程基线与真实 Schema 对齐

**Milestone Goal:** 将当前初始化仓库对齐到可承载业务开发的后端底座，统一 Spring Boot 版本、环境配置、通用响应异常、安全基线和真实 SQL 假设。

**Entry Condition:** 仓库只有最小启动类、最小测试、最小配置与初始 `pom.xml`。

**Exit Condition:** Boot 3.x 基线、`application.yml`、通用层、安全基线、`String UUID` 约束和 SQL 基线都已建立，阶段 0 测试通过。

### Task 1: 对齐 Maven 与配置骨架

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`
- Modify: `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`
- Create: `src/main/resources/application-prod.yml`
- Create: `src/test/java/com/jhun/backend/unit/config/ApplicationProfileConfigTest.java`

- [ ] **Step 1: 写失败测试，锁定配置基线**

```java
@SpringBootTest
@ActiveProfiles("test")
class ApplicationProfileConfigTest {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${jwt.access-token-validity}")
    private Long accessTokenValidity;

    @Test
    void shouldLoadTestProfileConfiguration() {
        assertEquals("device-management-backend", applicationName);
        assertEquals(86400L, accessTokenValidity);
    }
}
```

- [ ] **Step 2: 运行测试，确认当前配置骨架不足而失败**

Run: `mvn -Dtest=ApplicationProfileConfigTest,DeviceManagementBackendApplicationTests test`
Expected: FAIL，提示缺少配置项或 Boot 依赖不匹配

- [ ] **Step 3: 对齐 `pom.xml` 与多环境配置**

实施要点：

- Spring Boot 父 POM 回落到稳定 `3.x`
- 补齐 Web、Security、Validation、Redis、Mail、MyBatis-Plus、MySQL、JWT、Spring AI 依赖
- 保留 `com.jhun.backend` 包根与 `DeviceManagementBackendApplication` 类名
- 配置中至少显式包含：数据源、Redis、JWT、邮件、AI、上传、验证码、预约、逾期等配置组

- [ ] **Step 4: 回归测试**

Run: `mvn -Dtest=ApplicationProfileConfigTest,DeviceManagementBackendApplicationTests test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add pom.xml src/main/resources/application* src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java src/test/java/com/jhun/backend/unit/config/ApplicationProfileConfigTest.java
git commit -m "chore(core): align boot baseline and profile config"
```

### Task 2: 建立通用层、安全基线与 SQL 脚本

**Files:**
- Create: `src/main/java/com/jhun/backend/config/**`
- Create: `src/main/java/com/jhun/backend/common/**`
- Create: `src/main/java/com/jhun/backend/util/**`
- Create: `src/main/resources/sql/01_schema.sql`
- Create: `src/main/resources/sql/02_seed_roles.sql`
- Create: `src/main/resources/sql/03_seed_permissions.sql`
- Create: `src/main/resources/sql/04_seed_role_permissions.sql`
- Create: `src/test/java/com/jhun/backend/unit/common/GlobalExceptionHandlerTest.java`
- Create: `src/test/java/com/jhun/backend/integration/security/SecurityBootstrapIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/Stage0SmokeIT.java`

- [ ] **Step 1: 写失败测试，锁定响应、安全和 UUID 假设**

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBootstrapIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectAnonymousProtectedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }
}
```

```java
class GlobalExceptionHandlerTest {

    @Test
    void shouldWrapBusinessExceptionWithUnifiedBody() {
        Result<Void> body = new GlobalExceptionHandler()
            .handleBusinessException(new BusinessException("user_not_found"));
        assertEquals("user_not_found", body.getMessage());
    }
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -Dtest=GlobalExceptionHandlerTest,SecurityBootstrapIntegrationTest,Stage0SmokeIT test`
Expected: FAIL，提示通用层或安全配置尚未建立

- [ ] **Step 3: 按 SQL 新口径建立基线**

关键要求：

- `01_schema.sql` 必须以 `device_management.sql` 为蓝本拆分，不得保留 toy schema
- 枚举至少包含：三角色、审批模式、预约模式、预约状态、预约批次状态、通知渠道、通知状态、统计粒度、统计对象类型
- 通知渠道必须包含 `IN_APP`
- 预约状态必须使用 `PENDING_DEVICE_APPROVAL` / `PENDING_SYSTEM_APPROVAL` / `PENDING_MANUAL`
- 安全配置默认保护除白名单外的所有接口
- UUID 工具返回字符串，不得返回数字 ID

- [ ] **Step 4: 建立 Smoke 测试基座并回归**

Run: `mvn -Dtest=GlobalExceptionHandlerTest,SecurityBootstrapIntegrationTest,Stage0SmokeIT test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add src/main/java/com/jhun/backend/config src/main/java/com/jhun/backend/common src/main/java/com/jhun/backend/util src/main/resources/sql src/test/java/com/jhun/backend/unit/common src/test/java/com/jhun/backend/integration
git commit -m "chore(core): add common layer security baseline and schema scripts"
```

## Chunk 2: 身份、角色与通知底座

**Milestone Goal:** 建立认证、用户管理、角色权限、通知底座和已读能力，让三角色鉴权和前端通知页能尽早联调。

**Entry Condition:** 工程基线、统一响应异常、安全配置和 SQL 基线已就绪。

**Exit Condition:** 认证与用户接口可用，角色权限接口可用，通知列表与已读接口可用，`C-09` / `C-10` 落地。

### Task 3: 认证、个人资料与密码历史

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/User.java`
- Create: `src/main/java/com/jhun/backend/entity/Role.java`
- Create: `src/main/java/com/jhun/backend/entity/Permission.java`
- Create: `src/main/java/com/jhun/backend/entity/RolePermission.java`
- Create: `src/main/java/com/jhun/backend/entity/PasswordHistory.java`
- Create: `src/main/java/com/jhun/backend/mapper/UserMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/RoleMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PermissionMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/RolePermissionMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PasswordHistoryMapper.java`
- Create: `src/main/java/com/jhun/backend/dto/auth/**`
- Create: `src/main/java/com/jhun/backend/service/AuthService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/AuthController.java`
- Create: `src/test/java/com/jhun/backend/unit/service/AuthServiceTest.java`
- Create: `src/test/java/com/jhun/backend/integration/auth/AuthControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定认证边界**

```java
@Test
void shouldRegisterUserWithDefaultRoleUser() {
    RegisterRequest request = new RegisterRequest("zhangsan", "Password123", "zhangsan@example.com", "张三", "13800138000");
    LoginResponse response = authService.register(request);
    assertEquals("USER", response.getRole());
}

@Test
void shouldRejectReusedPasswordWhenResetting() {
    assertThrows(BusinessException.class, () -> authService.resetPassword(resetRequest));
}
```

- [ ] **Step 2: 运行测试，确认认证链尚未实现**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerIntegrationTest test`
Expected: FAIL

- [ ] **Step 3: 实现认证链路**

必须覆盖：

- 注册默认角色为 `USER`
- 登录账号可用用户名或邮箱
- 生成 Access Token / Refresh Token
- 实现 `GET /api/auth/me`、`PUT /api/auth/profile`、`POST /api/auth/change-password`
- 验证码发送与校验、密码重置、历史密码校验
- 登录失败 5 次锁定 30 分钟
- 不在正式 SQL seed 中硬编码管理员账号；管理员测试账号使用 `dev/test` 专用 seed 或 bootstrap 方式提供

- [ ] **Step 4: 回归测试**

Run: `mvn -Dtest=AuthServiceTest,AuthControllerIntegrationTest test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add src/main/java/com/jhun/backend/entity src/main/java/com/jhun/backend/dto/auth src/main/java/com/jhun/backend/service/AuthService.java src/main/java/com/jhun/backend/service/impl/AuthServiceImpl.java src/main/java/com/jhun/backend/controller/AuthController.java src/main/java/com/jhun/backend/mapper src/main/resources/mapper src/test/java/com/jhun/backend/unit/service/AuthServiceTest.java src/test/java/com/jhun/backend/integration/auth/AuthControllerIntegrationTest.java
git commit -m "feat(auth): implement authentication profile and password history workflow"
```

### Task 4: 用户管理、角色权限与系统运维任务

**Files:**
- Create: `src/main/java/com/jhun/backend/dto/user/**`
- Create: `src/main/java/com/jhun/backend/dto/role/**`
- Create: `src/main/java/com/jhun/backend/service/UserService.java`
- Create: `src/main/java/com/jhun/backend/service/RoleService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/RoleServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/UserController.java`
- Create: `src/main/java/com/jhun/backend/controller/RoleController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/system/TokenCleanupProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/system/SessionTimeoutProcessor.java`
- Create: `src/test/java/com/jhun/backend/integration/user/UserControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/role/RoleControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定角色与用户管理边界**

```java
@Test
void shouldAllowSystemAdminToDisableUser() throws Exception {
    mockMvc.perform(put("/api/admin/users/{id}/status", userId)
            .header("Authorization", bearer(systemAdminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":0,\"reason\":\"违规使用设备\"}"))
        .andExpect(status().isOk());
}

@Test
void shouldRejectDeviceAdminUpdatingRolePermissions() throws Exception {
    mockMvc.perform(put("/api/admin/roles/{id}/permissions", roleId)
            .header("Authorization", bearer(deviceAdminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionIds\":[\"p1\"]}"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -Dtest=UserControllerIntegrationTest,RoleControllerIntegrationTest test`
Expected: FAIL

- [ ] **Step 3: 实现用户与角色能力**

必须覆盖：

- `PUT /api/admin/users/{id}/status`
- `PUT /api/admin/users/{id}/role`
- `POST /api/admin/users/{id}/freeze` 或等价冻结接口，维护 `NORMAL / RESTRICTED / FROZEN`
- `GET /api/admin/roles`
- `PUT /api/admin/roles/{id}/permissions`
- `SYSTEM_ADMIN` 负责用户状态、冻结解冻、角色权限
- `DEVICE_ADMIN` 不得修改角色权限，不得执行冻结解冻
- `C-09` / `C-10` 在本 Chunk 一并落地

- [ ] **Step 4: 回归测试**

Run: `mvn -Dtest=UserControllerIntegrationTest,RoleControllerIntegrationTest test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add src/main/java/com/jhun/backend/dto/user src/main/java/com/jhun/backend/dto/role src/main/java/com/jhun/backend/service/UserService.java src/main/java/com/jhun/backend/service/RoleService.java src/main/java/com/jhun/backend/service/impl/UserServiceImpl.java src/main/java/com/jhun/backend/service/impl/RoleServiceImpl.java src/main/java/com/jhun/backend/controller/UserController.java src/main/java/com/jhun/backend/controller/RoleController.java src/main/java/com/jhun/backend/scheduler/system src/test/java/com/jhun/backend/integration/user/UserControllerIntegrationTest.java src/test/java/com/jhun/backend/integration/role/RoleControllerIntegrationTest.java
git commit -m "feat(user): add admin user role management and system schedulers"
```

### Task 5: 通知列表、未读数与已读接口

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/NotificationRecord.java`
- Create: `src/main/java/com/jhun/backend/mapper/NotificationRecordMapper.java`
- Create: `src/main/resources/mapper/NotificationRecordMapper.xml`
- Create: `src/main/java/com/jhun/backend/dto/notification/**`
- Create: `src/main/java/com/jhun/backend/service/NotificationService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/NotificationServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/notification/**`
- Create: `src/main/java/com/jhun/backend/controller/NotificationController.java`
- Create: `src/main/resources/templates/email/**`
- Create: `src/test/java/com/jhun/backend/integration/notification/NotificationControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定站内信与已读行为**

```java
@Test
void shouldReturnUnreadCount() throws Exception {
    mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(userToken)))
        .andExpect(status().isOk());
}

@Test
void shouldMarkNotificationAsRead() throws Exception {
    mockMvc.perform(put("/api/notifications/{id}/read", notificationId).header("Authorization", bearer(userToken)))
        .andExpect(status().isOk());
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -Dtest=NotificationControllerIntegrationTest test`
Expected: FAIL

- [ ] **Step 3: 实现通知底座与已读接口**

必须覆盖：

- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{id}/read`
- `PUT /api/notifications/read-all`
- `IN_APP` 为正式实现，`EMAIL` 为正式发送实现，`SMS` 仅占位
- 已读能力仅对 `IN_APP` 生效
- 模板、渠道、重试、已读、关联业务字段正确落库
- 同步把通知接口作为“需回写 API 文档”的文档任务记录下来

- [ ] **Step 4: 回归测试**

Run: `mvn -Dtest=NotificationControllerIntegrationTest test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add src/main/java/com/jhun/backend/entity/NotificationRecord.java src/main/java/com/jhun/backend/dto/notification src/main/java/com/jhun/backend/service/NotificationService.java src/main/java/com/jhun/backend/service/impl/NotificationServiceImpl.java src/main/java/com/jhun/backend/service/support/notification src/main/java/com/jhun/backend/controller/NotificationController.java src/main/resources/templates/email src/test/java/com/jhun/backend/integration/notification/NotificationControllerIntegrationTest.java
git commit -m "feat(notification): add notification query unread and read APIs"
```

## Chunk 3: 设备与分类主数据

**Milestone Goal:** 交付设备分类、设备 CRUD、图片上传、状态流转与状态日志，为预约和借还提供稳定主数据。

**Entry Condition:** 认证、角色和通知底座已可用。

**Exit Condition:** 设备页与分类页所需接口齐备，设备状态流转与状态日志可回传。

### Task 6: 分类树与设备 CRUD

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/DeviceCategory.java`
- Create: `src/main/java/com/jhun/backend/entity/Device.java`
- Create: `src/main/java/com/jhun/backend/dto/category/**`
- Create: `src/main/java/com/jhun/backend/dto/device/**`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceCategoryMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceMapper.java`
- Create: `src/main/resources/mapper/DeviceCategoryMapper.xml`
- Create: `src/main/resources/mapper/DeviceMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/CategoryService.java`
- Create: `src/main/java/com/jhun/backend/service/DeviceService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/CategoryServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/DeviceServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/DeviceCategoryController.java`
- Create: `src/main/java/com/jhun/backend/controller/DeviceController.java`
- Create: `src/test/java/com/jhun/backend/integration/category/CategoryControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/device/DeviceControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定分类与编号唯一性**
- [ ] **Step 2: 运行 `mvn -Dtest=CategoryControllerIntegrationTest,DeviceControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现分类、设备、分页查询与软删除**
- [ ] **Step 4: 回归测试并确认 `BUILD SUCCESS`**
- [ ] **Step 5: 提交建议 `feat(device): add device category and device CRUD APIs`**

### Task 7: 图片上传、设备状态机与状态日志

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/DeviceStatusLog.java`
- Create: `src/main/java/com/jhun/backend/mapper/DeviceStatusLogMapper.java`
- Create: `src/main/resources/mapper/DeviceStatusLogMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/support/device/DeviceImageStorageSupport.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/DeviceServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/controller/DeviceController.java`
- Create: `src/test/java/com/jhun/backend/unit/service/DeviceServiceTest.java`

- [ ] **Step 1: 写失败测试，锁定上传与状态日志回传**
- [ ] **Step 2: 运行 `mvn -Dtest=DeviceServiceTest,DeviceControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现 `POST /api/devices/{id}/image`、设备状态变更日志和详情回传**
- [ ] **Step 4: 确保 `BORROWED -> AVAILABLE` 只能通过归还流程完成**
- [ ] **Step 5: 回归测试并提交建议 `feat(device): add image upload status workflow and device traceability`**

## Chunk 4: 预约双审批、代预约与批量预约

**Milestone Goal:** 完成预约核心模型、双审批、代预约、批量预约、签到和预约类定时任务，形成 SQL 新口径下的主业务链上半段。

**Entry Condition:** 设备与分类主数据已可用。

**Exit Condition:** 预约状态机、审批职责、预约批次和签到/人工处理规则都可联调并具备自动化验证。

### Task 8: 预约核心模型、一审/二审与冲突检测

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/Reservation.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/CreateReservationRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/UpdateReservationRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/QueryReservationRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/ReservationResponse.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/AuditReservationRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/ConflictCheckResponse.java`
- Create: `src/main/java/com/jhun/backend/mapper/ReservationMapper.java`
- Create: `src/main/resources/mapper/ReservationMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/ReservationService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/ReservationServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/reservation/ReservationValidator.java`
- Create: `src/main/java/com/jhun/backend/service/support/reservation/ConflictDetector.java`
- Create: `src/main/java/com/jhun/backend/controller/ReservationController.java`
- Create: `src/test/java/com/jhun/backend/unit/service/support/ReservationValidatorTest.java`
- Create: `src/test/java/com/jhun/backend/unit/service/support/ConflictDetectorTest.java`
- Create: `src/test/java/com/jhun/backend/integration/reservation/ReservationControllerIntegrationTest.java`
- Create: `src/test/java/com/jhun/backend/integration/reservation/ReservationConflictConcurrencyIT.java`

- [ ] **Step 1: 写失败测试，锁定新预约状态机**

```java
@Test
void shouldCreateReservationWithDeviceApprovalStatus() { /* DEVICE_ONLY -> PENDING_DEVICE_APPROVAL */ }

@Test
void shouldMoveToSystemApprovalAfterFirstApproval() { /* DEVICE_THEN_SYSTEM */ }

@Test
void shouldRejectSecondApprovalBySameAccount() { /* same account cannot finish both reviews */ }
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -Dtest=ReservationValidatorTest,ConflictDetectorTest,ReservationControllerIntegrationTest,ReservationConflictConcurrencyIT test`
Expected: FAIL

- [ ] **Step 3: 实现预约创建、查询、审核与冲突检测**

必须满足：

- 按审批模式进入 `PENDING_DEVICE_APPROVAL` 或后续 `PENDING_SYSTEM_APPROVAL`
- 第一审仅 `DEVICE_ADMIN` 可执行，第二审仅 `SYSTEM_ADMIN` 可执行
- 同一账号不得完成双审两步
- `USER` 只能操作自己的预约
- 50 并发同设备同时间段预约必须 100% 拦截冲突
- 审核通知至少覆盖 `FIRST_APPROVAL_TODO`、`SECOND_APPROVAL_TODO`、`APPROVAL_PASSED`、`APPROVAL_REJECTED`

- [ ] **Step 4: 回归测试**

Run: `mvn -Dtest=ReservationValidatorTest,ConflictDetectorTest,ReservationControllerIntegrationTest,ReservationConflictConcurrencyIT test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交建议**

```bash
git add src/main/java/com/jhun/backend/entity/Reservation.java src/main/java/com/jhun/backend/dto/reservation src/main/java/com/jhun/backend/service/support/reservation src/main/java/com/jhun/backend/controller/ReservationController.java src/test/java/com/jhun/backend/unit/service/support src/test/java/com/jhun/backend/integration/reservation
git commit -m "feat(reservation): add reservation approval workflow and concurrency-safe conflict detection"
```

### Task 9: 代预约、批量预约与预约批次

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/ReservationBatch.java`
- Create: `src/main/java/com/jhun/backend/dto/reservationbatch/CreateReservationBatchRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservationbatch/ReservationBatchResponse.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/ProxyReservationRequest.java`
- Create: `src/main/java/com/jhun/backend/mapper/ReservationBatchMapper.java`
- Create: `src/main/resources/mapper/ReservationBatchMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/ReservationBatchService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/ReservationBatchServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/ReservationBatchController.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/ReservationServiceImpl.java`
- Create: `src/test/java/com/jhun/backend/integration/reservationbatch/ReservationBatchControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定代预约与批量预约边界**
- [ ] **Step 2: 运行 `mvn -Dtest=ReservationBatchControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现 `SYSTEM_ADMIN` 代 `USER` 预约，并同时实现 `USER` 本人批量预约与 `SYSTEM_ADMIN` 管理型批量预约、预约批次查询和结果汇总**
- [ ] **Step 4: 确保 `reservation_mode`、`created_by`、`batch_id`、批次状态与通知类型 `BATCH_RESERVATION_RESULT` / `ON_BEHALF_CREATED` 正确落地**
- [ ] **Step 5: 回归测试并提交建议 `feat(reservation): add proxy reservation and reservation batch workflow`**

### Task 10: 签到、待人工处理与预约类定时任务

**Files:**
- Create: `src/main/java/com/jhun/backend/dto/reservation/CheckInRequest.java`
- Create: `src/main/java/com/jhun/backend/dto/reservation/ManualProcessRequest.java`
- Modify: `src/main/java/com/jhun/backend/service/ReservationService.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/ReservationServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/controller/ReservationController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationAuditTimeoutReminder.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationAutoExpireProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationCheckInTimeoutProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/BorrowConfirmTimeoutProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/reservation/ReservationUpcomingReminder.java`
- Create: `src/test/java/com/jhun/backend/unit/service/ReservationServiceTest.java`

- [ ] **Step 1: 写失败测试，锁定签到窗口与 `PENDING_MANUAL` 语义**
- [ ] **Step 2: 运行 `mvn -Dtest=ReservationServiceTest,ReservationControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现签到、人工处理与任务 C-01 / C-02 / C-03 / C-04 / C-11**
- [ ] **Step 4: 确保 `CHECKIN_TIMEOUT_WARNING`、`BORROW_CONFIRM_WARNING`、`PENDING_MANUAL_NOTICE` 正确触发**
- [ ] **Step 5: 回归测试并提交建议 `feat(reservation): add check-in manual process and reservation schedulers`**

## Chunk 5: 借还与逾期治理

**Milestone Goal:** 完成借用确认、归还确认、逾期识别、限制策略与通知联动，闭合主业务链。

**Entry Condition:** 预约双审批、签到和待人工处理逻辑已稳定。

**Exit Condition:** 借还页与逾期页可联调，C-05 / C-06 / C-07 可运行，冻结限制和通知联动正确。

### Task 11: 借用确认、归还确认与借还记录

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/BorrowRecord.java`
- Create: `src/main/java/com/jhun/backend/dto/borrow/**`
- Create: `src/main/java/com/jhun/backend/mapper/BorrowRecordMapper.java`
- Create: `src/main/resources/mapper/BorrowRecordMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/BorrowService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/BorrowServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/BorrowController.java`
- Create: `src/test/java/com/jhun/backend/integration/borrow/BorrowControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定借还状态联动**
- [ ] **Step 2: 运行 `mvn -Dtest=BorrowControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现借用确认、归还确认、列表与详情接口**
- [ ] **Step 4: 确保 `DEVICE_ADMIN` 才能执行借还确认，且设备状态严格跟随借还状态**
- [ ] **Step 5: 回归测试并提交建议 `feat(borrow): add borrow confirm return and record APIs`**

### Task 12: 逾期治理、限制释放与通知联动

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/OverdueRecord.java`
- Create: `src/main/java/com/jhun/backend/dto/overdue/**`
- Create: `src/main/java/com/jhun/backend/mapper/OverdueRecordMapper.java`
- Create: `src/main/resources/mapper/OverdueRecordMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/OverdueService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/OverdueServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/OverdueController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueAutoDetectProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueNotificationProcessor.java`
- Create: `src/main/java/com/jhun/backend/scheduler/overdue/OverdueRestrictionReleaseProcessor.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/NotificationServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/DeviceServiceImpl.java`
- Create: `src/test/java/com/jhun/backend/integration/overdue/OverdueControllerIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定逾期分段与冻结规则**
- [ ] **Step 2: 运行 `mvn -Dtest=OverdueControllerIntegrationTest,BorrowControllerIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现逾期识别、处理、限制释放与任务 C-05 / C-06 / C-07**
- [ ] **Step 4: 确保 `<4h` 仅警告、`RESTRICTED` 可自动释放、`FROZEN` 仅 `SYSTEM_ADMIN` 可解除，并补齐 `OVERDUE_WARNING` / `ACCOUNT_FREEZE_UNFREEZE` / `DEVICE_MAINTENANCE_NOTICE` 联动**
- [ ] **Step 5: 回归测试并提交建议 `feat(overdue): add overdue governance restrictions and schedulers`**

## Chunk 6: AI、统计与发布收口

**Milestone Goal:** 交付 AI 对话与 Prompt 模板、统计聚合与排名接口、发布资产与文档同步任务，完成发布前收口。

**Entry Condition:** 认证、设备、预约、借还、逾期主流程稳定。

**Exit Condition:** AI 页面、模板页、统计页可联调，系统达到 `mvn clean verify` 可通过的发布前状态。

### Task 13: AI 对话、历史与 Prompt 模板

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/ChatHistory.java`
- Create: `src/main/java/com/jhun/backend/entity/PromptTemplate.java`
- Create: `src/main/java/com/jhun/backend/dto/ai/**`
- Create: `src/main/java/com/jhun/backend/mapper/ChatHistoryMapper.java`
- Create: `src/main/java/com/jhun/backend/mapper/PromptTemplateMapper.java`
- Create: `src/main/resources/mapper/ChatHistoryMapper.xml`
- Create: `src/main/resources/mapper/PromptTemplateMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/AiService.java`
- Create: `src/main/java/com/jhun/backend/service/PromptTemplateService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/AiServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/impl/PromptTemplateServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/service/support/ai/**`
- Create: `src/main/java/com/jhun/backend/controller/AiController.java`
- Create: `src/main/java/com/jhun/backend/controller/PromptTemplateController.java`
- Create: `src/main/resources/templates/ai/**`
- Create: `src/test/java/com/jhun/backend/integration/ai/**`

- [ ] **Step 1: 写失败测试，锁定 AI 意图、历史与模板路径**
- [ ] **Step 2: 运行 `mvn -Dtest=AiControllerIntegrationTest,AiHistoryIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现 AI 对话、历史、规则降级和 `/api/ai/prompts*` 模板接口**
- [ ] **Step 4: 确保 AI 只通过业务服务执行操作，不直接写数据库，且 `DialogConfig` 不进入本任务范围**
- [ ] **Step 5: 回归测试并提交建议 `feat(ai): add chat history intent workflow and prompt template management`**

### Task 14: 统计聚合、发布资产与文档回写

**Files:**
- Create: `src/main/java/com/jhun/backend/entity/StatisticsDaily.java`
- Create: `src/main/java/com/jhun/backend/dto/statistics/**`
- Create: `src/main/java/com/jhun/backend/mapper/StatisticsDailyMapper.java`
- Create: `src/main/resources/mapper/StatisticsDailyMapper.xml`
- Create: `src/main/java/com/jhun/backend/service/StatisticsService.java`
- Create: `src/main/java/com/jhun/backend/service/impl/StatisticsServiceImpl.java`
- Create: `src/main/java/com/jhun/backend/controller/StatisticsController.java`
- Create: `src/main/java/com/jhun/backend/scheduler/statistics/StatisticsAggregationProcessor.java`
- Create: `src/main/resources/logback-spring.xml`
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-03-11-device-management-implementation-roadmap-design.md`
- Modify: `docs/superpowers/plans/2026-03-11-device-management-backend-implementation-plan.md`
- Create: `src/test/java/com/jhun/backend/integration/statistics/**`
- Create: `src/test/java/com/jhun/backend/integration/system/EndToEndSmokeIntegrationTest.java`

- [ ] **Step 1: 写失败测试，锁定统计聚合与排名接口**
- [ ] **Step 2: 运行 `mvn -Dtest=StatisticsControllerIntegrationTest,EndToEndSmokeIntegrationTest test`，确认失败**
- [ ] **Step 3: 实现统计接口，至少覆盖总览、设备利用率、分类利用率、借用统计、逾期统计、热门时段、设备排名、用户排名**
- [ ] **Step 4: 补齐 `README.md`、`Dockerfile`、`docker-compose.yml`、`logback-spring.xml`，并把通知接口与 SQL 新口径同步回写到文档**
- [ ] **Step 5: 执行 `mvn clean verify`，通过后提交建议 `feat(statistics): add aggregation ranking APIs and release assets`**

### 2.14 Task 14 实施回写（2026-03-14）

- 已先写失败测试：`StatisticsControllerIntegrationTest`、`EndToEndSmokeIntegrationTest`
- 已执行 `mvn -Dtest=StatisticsControllerIntegrationTest,EndToEndSmokeIntegrationTest test`，初次失败原因为统计控制器缺失、`statisticsAggregationProcessor` Bean 不存在
- 已实现 `statistics_daily` 聚合链路、`/api/statistics/*` 接口、`C-08` 统计预聚合任务和发布骨架文件
- 已同步 README、Docker、日志配置，并把通知接口家族与统计接口家族按 SQL 新口径回写到文档
- 若后续继续扩展统计范围，应继续坚持“查询层只读 `statistics_daily`，业务事实表只由聚合任务扫描”的边界

## 3. 文档缺口处理策略

以下事项在执行计划时必须显式处理，不允许静默跳过：

- **通知接口回写**：实现 `/api/notifications*` 后，同步更新 API 文档
- **旧口径修正**：角色、预约状态、通知渠道、通知已读、Prompt 接口路径等旧口径必须同步修正
- **DialogConfig**：先补单独设计，不在当前计划直接实现

## 4. 阶段性验证清单

每个 Chunk 完成后，至少执行：

- 当前 Chunk 对应单元测试 / 集成测试
- `mvn clean test`
- 阶段性代码审查
- 文档同步检查：`AGENTS.md`、路线设计、实施计划、README / SQL / API 说明

## 5. 执行完成定义

只有在以下条件全部满足时，才允许宣称“后端计划执行完成”：

- 所有 Chunk 的退出条件达成
- `mvn clean verify` 通过
- 三角色核心链路都至少完成一条端到端验证
- 通知已读、预约批次、三角色双审批、Prompt 模板路径等 SQL 新口径已在代码和文档中统一
- 文档与代码口径一致，不再存在 `Long ID`、错误包名、旧 `ADMIN`、旧 `PENDING` 或“通知已读缺字段”等历史假设
