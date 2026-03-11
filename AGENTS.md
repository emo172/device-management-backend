# AGENTS.md - 设备管理后端项目

本文档用于指导本仓库内的 Agentic Coding Agents 进行统一开发、测试与文档编写。

---

## 01 语言设置

- **回答语言**: 中文（简体）
- 所有 AI 文档、说明、注释建议使用中文（简体）
- **回答前缀**: 每次回答之前加 `喵`
- **回答后缀**: 回答完成之后加 `喵喵`

---

## 02 Git 分支管理

### 02-1 核心原则

禁止直接在 `main` 分支开发，所有开发任务必须在功能分支完成。

### 02-2 分支命名规范

```bash
# 功能开发
feature/模块名称
feature/device-management

# 缺陷修复
fix/问题描述
fix/login-token-expire

# 重构
refactor/模块名称
```

### 02-3 本地开发流程

```bash
# 1) 基于 main 创建新分支
git checkout main
git pull
git checkout -b feature/xxx

# 2) 开发并提交
git add .
git commit -m "feat: 新增 xxx 功能"

# 3) 合并前必须通过构建和测试
mvn clean verify
```

### 02-4 提交信息规范

```
<type>(<scope>): <subject>
```

常用类型：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`。

---

## 03 项目概述

### 03-1 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 语言与运行时 |
| Maven | 3.x | 构建与依赖管理 |
| Spring Boot | 3.x | 后端框架 |
| Spring Security | 6.x | 认证与权限 |
| MyBatis-Plus | Latest | 数据访问 |
| MySQL | 8.x | 关系型数据库 |
| Redis | Latest | 缓存与会话 |
| JWT | Latest | Token 认证 |
| Spring AI | Latest | LLM 集成 |

### 03-2 架构约定

- 使用三层架构：`Controller -> Service -> Mapper`
- 使用 `DTO/VO` 进行接口输入输出隔离
- 使用 `common/exception` 统一异常处理
- 使用 `common/response` 统一响应体

### 03-3 功能模块映射

| 模块 | 说明 | 对应需求章节 |
|------|------|--------------|
| 用户与权限 | 用户注册、登录、权限控制、冻结解冻 | 需求分析 #3.1 节 |
| 设备管理 | 设备 CRUD、分类管理、状态变更记录 | 需求分析 #3.2 节 |
| 预约管理 | 预约申请、审核、签到、人工处理 | 需求分析 #3.3 节 |
| 借还管理 | 借用确认、归还确认 | 需求分析 #3.4 节 |
| 逾期管理 | 逾期识别、处理、统计 | 需求分析 #3.5 节 |
| 智能对话 | AI 对话、意图识别、提示模板管理 | 需求分析 #3.6 节 |
| 统计分析 | 数据统计、预聚合 | 需求分析 #3.7 节 |

---

## 04 构建、测试与质量命令

### 04-1 Maven 常用命令

| 命令 | 说明 |
|------|------|
| `mvn clean compile` | 清理并编译 |
| `mvn clean test` | 运行全部测试 |
| `mvn clean package` | 打包（默认会跑测试） |
| `mvn clean package -DskipTests` | 跳过测试打包 |
| `mvn clean verify` | 执行完整校验流程 |
| `mvn spring-boot:run` | 本地启动服务 |

### 04-2 运行单个测试

```bash
# 单个测试类
mvn test -Dtest=DeviceServiceTest

# 单个测试方法
mvn test -Dtest=DeviceServiceTest#testCreateDevice

# 多个测试类
mvn test -Dtest=DeviceServiceTest,ReservationServiceTest
```

### 04-3 分层测试建议

```bash
# 仅单元测试（按包名）
mvn test -Dtest=unit.*

# 仅集成测试（按命名）
mvn test -Dtest=*IT,*IntegrationTest
```

### 04-4 质量检查

```bash
mvn checkstyle:check
mvn spotbugs:check
```

---

## 05 目录结构

### 05-1 项目结构概览

```
device-management-backend/
│
├── src/main/java/com/jhu/device/management/
│   │
│   ├── DeviceManagementApplication.java          # 应用启动类
│   │
│   ├── config/                                   # 配置模块
│   │   ├── AppConfig.java                       # 应用配置
│   │   ├── RedisConfig.java                     # Redis配置
│   │   ├── AsyncConfig.java                      # 异步任务配置
│   │   ├── WebConfig.java                       # Web配置（CORS等）
│   │   ├── ThreadPoolConfig.java                # 线程池配置
│   │   │
│   │   ├── security/                            # 安全配置
│   │   │   ├── SecurityConfig.java              # Spring Security配置
│   │   │   ├── JwtConfig.java                   # JWT配置
│   │   │   ├── JwtAuthenticationFilter.java    # JWT认证过滤器
│   │   │   ├── JwtAuthenticationEntryPoint.java # JWT认证失败处理
│   │   │   └── AccessDeniedHandlerImpl.java     # 权限不足处理
│   │   │
│   │   ├── mybatis/                             # MyBatis配置
│   │   │   ├── MybatisPlusConfig.java           # MyBatis-Plus配置
│   │   │   └── PaginationInterceptor.java       # 分页插件配置
│   │   │
│   │   ├── ai/                                  # AI配置
│   │   │   ├── AiConfig.java                   # AI服务配置
│   │   │   └── PromptConfig.java                # Prompt模板配置
│   │   │
│   │   └── email/                               # 邮件配置
│   │       └── EmailConfig.java                 # SMTP邮件配置
│   │
│   ├── common/                                  # 通用模块
│   │   ├── exception/                           # 异常类
│   │   ├── response/                            # 响应类
│   │   ├── constant/                            # 常量类
│   │   ├── annotation/                          # 自定义注解
│   │   ├── aspect/                             # AOP切面
│   │   ├── entity/                              # 实体基类
│   │   └── enums/                               # 通用枚举
│   │
│   ├── dto/                                    # 数据传输对象
│   │   ├── auth/                               # 认证DTO
│   │   ├── user/                               # 用户DTO
│   │   ├── device/                             # 设备DTO
│   │   ├── category/                           # 分类DTO
│   │   ├── reservation/                        # 预约DTO
│   │   ├── borrow/                             # 借还DTO
│   │   ├── overdue/                            # 逾期DTO
│   │   ├── ai/                                 # AI对话DTO
│   │   ├── notification/                       # 通知DTO
│   │   └── statistics/                         # 统计DTO
│   │
│   ├── entity/                                 # 实体类
│   │
│   ├── mapper/                                 # 数据访问层
│   │
│   ├── service/                                # 业务逻辑层
│   │   ├── impl/                              # 服务实现层
│   │   └── support/                           # 服务辅助组件
│   │
│   ├── controller/                           # 控制层
│   │
│   ├── scheduler/                            # 定时任务
│   │
│   └── util/                                 # 工具类
│
├── src/main/resources/
│   ├── mapper/                              # MyBatis XML映射文件
│   ├── templates/                          # 模板文件
│   │   ├── email/                          # 邮件模板
│   │   └── ai/                             # AI提示词模板
│   ├── application.yml                     # 主配置文件
│   ├── application-dev.yml                 # 开发环境配置
│   ├── application-prod.yml                # 生产环境配置
│   ├── application-test.yml                # 测试环境配置
│   └── logback-spring.xml                  # 日志配置
│
├── src/test/java/com/jhu/device/management/
│   ├── unit/                                # 单元测试
│   └── integration/                          # 集成测试
│
├── pom.xml                                 # Maven配置文件
├── Dockerfile                              # Docker构建文件
├── docker-compose.yml                      # Docker Compose配置
└── README.md                               # 项目说明
```

### 05-2 目录结构设计原则

#### Service 层组织

- **接口**：统一放在 `service/` 目录下
- **实现**：统一放在 `service/impl/` 目录下
- **支撑类**：放在 `service/support/` 目录下（如校验器、检测器、客户端等）

#### Common 模块组织

- **exception/**：异常相关类（全局异常处理器、异常基类、业务异常等）
- **response/**：响应相关类（统一响应结果、分页响应等）
- **constant/**：常量类（应用常量、Redis 键常量、错误码常量等）
- **annotation/**：自定义注解
- **aspect/**：AOP 切面
- **entity/**：实体基类
- **enums/**：通用枚举

---

## 06 模块详细说明

### 06-1 Controller 层

| Controller | 路径前缀 | 功能说明 |
|------------|----------|----------|
| AuthController | /api/auth | 登录、注册、Token刷新、密码找回、登出、验证码发送 |
| UserController | /api/admin/users | 用户管理、冻结解冻、权限分配 |
| DeviceController | /api/devices | 设备管理、图片上传 |
| CategoryController | /api/device-categories | 设备分类管理 |
| ReservationController | /api/reservations | 预约管理、签到、审核、人工处理、冲突检测 |
| BorrowController | /api/borrow-records | 借还管理 |
| OverdueController | /api/overdue-records | 逾期管理 |
| AiController | /api/ai | AI对话、提示模板管理 |
| StatisticsController | /api/statistics | 统计分析 |

### 06-2 Service 层

| Service | 说明 | 主要业务逻辑 |
|---------|------|--------------|
| AuthService | 认证服务 | 登录、注册、Token管理、验证码、密码重置 |
| UserService | 用户服务 | 用户CRUD、权限管理、冻结解冻 |
| DeviceService | 设备服务 | 设备CRUD、状态管理、图片上传、状态变更记录 |
| CategoryService | 分类服务 | 分类CRUD、二级分类管理 |
| ReservationService | 预约服务 | 预约创建、审核、签到、冲突检测、人工处理 |
| BorrowService | 借还服务 | 借用确认、归还确认、历史记录 |
| OverdueService | 逾期服务 | 逾期识别、处理、统计 |
| AiService | AI服务 | 对话、意图识别、槽位提取 |
| PromptTemplateService | 提示模板服务 | 模板CRUD、模板变量管理 |
| StatisticsService | 统计服务 | 数据统计、预聚合 |
| NotificationService | 通知服务 | 邮件发送、短信发送 |

### 06-3 定时任务

#### 06-3-1 预约管理相关

| 任务 | Cron | 功能 | 任务编号 |
|------|------|------|----------|
| ReservationAuditTimeoutReminder | `0 0 * * * ?` | 审核超时提醒 | C-01 |
| ReservationAutoExpireProcessor | `0 15 * * * ?` | 预约自动过期 | C-02 |
| ReservationCheckInTimeoutProcessor | `0 */15 * * * ?` | 签到超时处理 | C-03 |
| BorrowConfirmTimeoutProcessor | `0 */15 * * * ?` | 借用确认超时 | C-04 |
| ReservationUpcomingReminder | `0 */15 * * * ?` | 即将开始提醒 | C-11 |

#### 06-3-2 逾期管理相关

| 任务 | Cron | 功能 | 任务编号 |
|------|------|------|----------|
| OverdueAutoDetectProcessor | `0 0 * * * ?` | 逾期自动识别 | C-05 |
| OverdueNotificationProcessor | `0 30 * * * ?` | 逾期通知发送 | C-06 |
| OverdueRestrictionReleaseProcessor | `0 0 2 * * ?` | 限制自动解除 | C-07 |

#### 06-3-3 统计分析相关

| 任务 | Cron | 功能 | 任务编号 |
|------|------|------|----------|
| StatisticsAggregationProcessor | `0 30 2 * * ?` | 统计数据预聚合 | C-08 |

#### 06-3-4 系统运维相关

| 任务 | Cron | 功能 | 任务编号 |
|------|------|------|----------|
| TokenCleanupProcessor | `0 0 3 * * ?` | Token过期清理 | C-09 |
| SessionTimeoutProcessor | `0 */10 * * * ?` | 会话空闲超时 | C-10 |

### 06-4 数据库表对应

| Entity | 对应表 | 说明 |
|--------|--------|------|
| User | user | 用户表 |
| Role | role | 角色表 |
| Permission | permission | 权限表 |
| RolePermission | role_permission | 角色权限关联表 |
| Device | device | 设备表 |
| DeviceCategory | device_category | 设备分类表 |
| DeviceStatusChangeLog | device_status_log | 设备状态变更记录表 |
| Reservation | reservation | 预约表 |
| BorrowRecord | borrow_record | 借还记录表 |
| OverdueRecord | overdue_record | 逾期记录表 |
| ChatHistory | chat_history | AI对话历史表 |
| PromptTemplate | prompt_template | AI提示模板表 |
| NotificationRecord | notification_record | 通知记录表 |
| StatisticsDaily | statistics_daily | 统计数据聚合表 |

### 06-5 枚举类说明

#### 06-5-1 设备状态（DeviceStatus）

| 状态 | 说明 |
|------|------|
| AVAILABLE | 可用 |
| BORROWED | 已借出 |
| MAINTENANCE | 维护中 |
| DISABLED | 已禁用 |
| DELETED | 已删除 |

#### 06-5-2 预约状态（ReservationStatus）

| 状态 | 说明 |
|------|------|
| PENDING | 待审核（自动审核通过） |
| PENDING_MANUAL | 待人工审核 |
| APPROVED | 已通过 |
| REJECTED | 已拒绝 |
| CANCELLED | 已取消 |
| EXPIRED | 已过期 |

#### 06-5-3 签到状态（CheckInStatus）

| 状态 | 说明 |
|------|------|
| NOT_CHECKED_IN | 未签到 |
| CHECKED_IN | 已签到 |
| CHECKED_IN_TIMEOUT | 签到超时 |

#### 06-5-4 冻结状态（FreezeStatus）

| 状态 | 说明 |
|------|------|
| NORMAL | 正常 |
| RESTRICTED | 受限（逾期导致） |
| FROZEN | 冻结（管理员操作） |

#### 06-5-5 通知类型（NotificationType）

| 类型 | 说明 | 接收者 |
|------|------|--------|
| VERIFY_CODE | 验证码 | 用户 |
| APPROVAL_PASSED | 审核通过 | 用户 |
| APPROVAL_REJECTED | 审核拒绝 | 用户 |
| RESERVATION_REMINDER | 预约提醒 | 用户 |
| CHECKIN_TIMEOUT_WARNING | 签到超时预警 | 用户 |
| BORROW_CONFIRM_WARNING | 借用确认预警 | 管理员 |
| OVERDUE_WARNING | 逾期提醒 | 用户 |
| REVIEW_TIMEOUT_WARNING | 审核超时提醒 | 管理员 |
| RESERVATION_CANCELLED | 预约取消 | 用户 |
| ACCOUNT_FREEZE_UNFREEZE | 冻结/解冻 | 用户 |

#### 06-5-6 通知状态（NotificationStatus）

| 状态 | 说明 |
|------|------|
| PENDING | 待发送 |
| SENDING | 发送中 |
| SUCCESS | 发送成功 |
| FAILED | 发送失败 |

---

## 07 配置文件说明

### 07-1 application.yml 核心配置

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/device_management
    username: root
    password: ${DB_PASSWORD}
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD}
  mail:
    host: smtp.example.com
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  access-token-validity: 86400   # 24小时
  refresh-token-validity: 604800 # 7天

ai:
  provider: qianwen
  api-key: ${AI_API_KEY}
  model: qwen-turbo
  timeout: 10000

device:
  upload:
    path: /data/uploads
    max-size: 2MB

# 验证码配置
verification:
  code:
    expire-minutes: 15
    max-attempts: 5
    daily-limit-per-email: 3
    hourly-limit-per-ip: 5

# 预约配置
reservation:
  open-hours: "08:00-22:00"
  min-duration-minutes: 30
  max-duration-days: 7
  cancel-free-hours: 24

# 逾期配置
overdue:
  detection-cron: "0 0 * * * ?"
  restriction-days: 7
```

### 07-2 Redis 键设计

| 键前缀 | 说明 | 示例 |
|--------|------|------|
| token:access: | Access Token | token:access:user_uuid |
| token:refresh: | Refresh Token | token:refresh:user_uuid |
| verify:code: | 验证码 | verify:code:email |
| reservation:lock: | 预约锁 | reservation:lock:device_id:time |
| session:last: | 最后活跃时间 | session:last:user_id |

---

## 08 命名规范

### 08-1 类命名规范

| 类型 | 命名规范 | 示例 |
|------|----------|------|
| 实体类 | 名词，PascalCase | `User`, `Device`, `Reservation` |
| Service接口 | 名词+Service | `UserService`, `DeviceService` |
| Service实现 | 接口名+Impl | `UserServiceImpl`, `DeviceServiceImpl` |
| Controller | 名词+Controller | `UserController`, `DeviceController` |
| Mapper | 实体名+Mapper | `UserMapper`, `DeviceMapper` |
| DTO请求 | 动词+名词+Request | `CreateUserRequest`, `UpdateDeviceRequest` |
| DTO响应 | 名词+Response | `UserResponse`, `DeviceResponse` |
| 常量类 | 名词+Constants 或 UPPER_SNAKE_CASE | `RedisKeyConstants`, `ErrorConstants` |
| 工具类 | 名词+Util | `JwtUtil`, `DateUtil` |
| 异常类 | 名词+Exception | `BusinessException`, `AuthException` |

### 08-2 DTO 命名规范

| 操作类型 | 命名格式 | 示例 |
|----------|----------|------|
| 创建 | Create+实体名+Request | `CreateUserRequest` |
| 更新 | Update+实体名+Request | `UpdateUserRequest` |
| 查询 | Query+实体名+Request | `QueryDeviceRequest` |
| 删除 | Delete+实体名+Request | `DeleteReservationRequest` |
| 动作 | 动作+实体名+Request | `FreezeUserRequest`, `ConfirmBorrowRequest` |
| 响应 | 实体名+Response | `UserResponse`, `DeviceResponse` |

---

## 09 与前端接口对应关系

| 前端路由 | 后端Controller | 接口路径 |
|----------|----------------|----------|
| /login | AuthController | POST /api/auth/login |
| /register | AuthController | POST /api/auth/register |
| /forgot-password | AuthController | POST /api/auth/send-verification-code |
| /reset-password | AuthController | POST /api/auth/reset-password |
| /logout | AuthController | POST /api/auth/logout |
| /devices | DeviceController | GET/POST /api/devices |
| /reservations | ReservationController | GET/POST /api/reservations |
| /reservations/:id/check-in | ReservationController | POST /api/reservations/{id}/check-in |
| /reservations/:id/manual-process | ReservationController | POST /api/reservations/{id}/manual-process |
| /borrows/confirm | BorrowController | POST /api/borrow-records/{reservationId}/borrow |
| /borrows/return | BorrowController | POST /api/borrow-records/{id}/return |
| /ai/chat | AiController | POST /api/ai/chat |
| /statistics | StatisticsController | GET /api/statistics/overview |

---

## 附录：详细目录清单

### config/ 目录

| 文件 | 说明 |
|------|------|
| AppConfig.java | 应用配置 |
| RedisConfig.java | Redis配置 |
| AsyncConfig.java | 异步任务配置 |
| WebConfig.java | Web配置（CORS等） |
| ThreadPoolConfig.java | 线程池配置 |

#### security/ 子目录

| 文件 | 说明 |
|------|------|
| SecurityConfig.java | Spring Security配置 |
| JwtConfig.java | JWT配置 |
| JwtAuthenticationFilter.java | JWT认证过滤器 |
| JwtAuthenticationEntryPoint.java | JWT认证失败处理 |
| AccessDeniedHandlerImpl.java | 权限不足处理 |

#### mybatis/ 子目录

| 文件 | 说明 |
|------|------|
| MybatisPlusConfig.java | MyBatis-Plus配置 |
| PaginationInterceptor.java | 分页插件配置 |

#### ai/ 子目录

| 文件 | 说明 |
|------|------|
| AiConfig.java | AI服务配置 |
| PromptConfig.java | Prompt模板配置 |

#### email/ 子目录

| 文件 | 说明 |
|------|------|
| EmailConfig.java | SMTP邮件配置 |

### dto/ 目录

#### auth/

| 文件 | 说明 |
|------|------|
| LoginRequest.java | 登录请求 |
| RegisterRequest.java | 注册请求 |
| RefreshTokenRequest.java | 刷新Token请求 |
| SendVerificationCodeRequest.java | 发送验证码请求 |
| ResetPasswordRequest.java | 重置密码请求 |
| LoginResponse.java | 登录响应 |
| VerificationCodeResponse.java | 验证码响应 |
| ResetTokenResponse.java | 重置令牌响应 |

#### user/

| 文件 | 说明 |
|------|------|
| CreateUserRequest.java | 创建用户请求 |
| UpdateUserRequest.java | 更新用户请求 |
| QueryUserRequest.java | 用户查询请求 |
| UserResponse.java | 用户响应 |
| FreezeUserRequest.java | 用户冻结请求 |
| UnfreezeUserRequest.java | 用户解冻请求 |
| AssignPermissionRequest.java | 权限分配请求 |

#### device/

| 文件 | 说明 |
|------|------|
| CreateDeviceRequest.java | 创建设备请求 |
| UpdateDeviceRequest.java | 更新设备请求 |
| QueryDeviceRequest.java | 设备查询请求 |
| DeviceResponse.java | 设备响应 |

#### category/

| 文件 | 说明 |
|------|------|
| CreateCategoryRequest.java | 创建分类请求 |
| UpdateCategoryRequest.java | 更新分类请求 |
| QueryCategoryRequest.java | 分类查询请求 |
| CategoryResponse.java | 分类响应 |

#### reservation/

| 文件 | 说明 |
|------|------|
| CreateReservationRequest.java | 创建预约请求 |
| UpdateReservationRequest.java | 更新预约请求 |
| QueryReservationRequest.java | 预约查询请求 |
| ReservationResponse.java | 预约响应 |
| AuditReservationRequest.java | 预约审核请求 |
| CheckInRequest.java | 签到请求 |
| ManualProcessRequest.java | 人工处理请求 |
| ConflictCheckResponse.java | 冲突检测响应 |

#### borrow/

| 文件 | 说明 |
|------|------|
| ConfirmBorrowRequest.java | 借用确认请求 |
| ReturnBorrowRequest.java | 归还确认请求 |
| QueryBorrowRequest.java | 借还查询请求 |
| BorrowResponse.java | 借还响应 |

#### overdue/

| 文件 | 说明 |
|------|------|
| HandleOverdueRequest.java | 逾期处理请求 |
| QueryOverdueRequest.java | 逾期查询请求 |
| OverdueResponse.java | 逾期响应 |
| OverdueStatisticsResponse.java | 逾期统计响应 |

#### ai/

| 文件 | 说明 |
|------|------|
| AiChatRequest.java | AI对话请求 |
| AiChatResponse.java | AI对话响应 |
| AiIntentResult.java | 意图识别结果 |
| AiSlotInfo.java | 槽位信息 |
| CreatePromptTemplateRequest.java | 创建提示模板请求 |
| UpdatePromptTemplateRequest.java | 更新提示模板请求 |
| PromptTemplateResponse.java | 提示模板响应 |

#### notification/

| 文件 | 说明 |
|------|------|
| NotificationResponse.java | 通知响应DTO |

#### statistics/

| 文件 | 说明 |
|------|------|
| QueryStatisticsRequest.java | 统计查询请求 |
| OverviewResponse.java | 统计概览响应 |
| DeviceUsageResponse.java | 设备利用率响应 |
| BorrowStatsResponse.java | 借用统计响应 |
| HotTimeSlotResponse.java | 热门时段响应 |

### scheduler/ 目录

#### reservation/

| 文件 | 说明 |
|------|------|
| ReservationAuditTimeoutReminder.java | 审核超时提醒 |
| ReservationAutoExpireProcessor.java | 预约自动过期 |
| ReservationCheckInTimeoutProcessor.java | 签到超时处理 |
| BorrowConfirmTimeoutProcessor.java | 借用确认超时 |
| ReservationUpcomingReminder.java | 即将开始提醒 |

#### overdue/

| 文件 | 说明 |
|------|------|
| OverdueAutoDetectProcessor.java | 逾期自动识别 |
| OverdueNotificationProcessor.java | 逾期通知发送 |
| OverdueRestrictionReleaseProcessor.java | 限制自动解除 |

#### statistics/

| 文件 | 说明 |
|------|------|
| StatisticsAggregationProcessor.java | 统计数据预聚合 |

#### system/

| 文件 | 说明 |
|------|------|
| TokenCleanupProcessor.java | Token过期清理 |
| SessionTimeoutProcessor.java | 会话超时检查 |

### service/support/ 目录

| 文件 | 说明 |
|------|------|
| ReservationValidator.java | 预约校验器 |
| ConflictDetector.java | 冲突检测器 |
| IntentRecognizer.java | 意图识别器 |
| SlotExtractor.java | 槽位提取器 |
| PromptEngine.java | Prompt引擎 |
| LlmClient.java | LLM客户端接口 |
| LlmClientImpl.java | LLM客户端实现 |
| EmailSender.java | 邮件发送器 |
| SmsSender.java | 短信发送器（预留） |
| NotificationTemplate.java | 通知模板 |
