# AGENTS.md - 设备管理后端项目

本文档用于指导本仓库内的 Agentic Coding Agents 统一开展后端设计、开发、测试、联调与文档维护工作。

---

## 01 语言设置

- **回答语言**: 中文（简体）
- 所有 AI 文档、说明、注释建议使用中文（简体）
- **回答前缀**: 每次回答之前加 `喵`
- **回答后缀**: 回答完成之后加 `喵喵`

---

## 02 真相源与冲突仲裁

### 02-1 资料真相源

后端实现、计划与文档维护时，默认以下资料为真相源：

1. `系统功能设计/` 目录下的 13 份系统设计文档
2. `后端项目目录结构.md`
3. `前端项目目录结构.md`
4. `device_management.sql`
5. 当前仓库实际文件结构（仅用于判断包路径、启动类、依赖现状与已有实现）

### 02-2 冲突处理规则

当资料之间出现不一致时，按以下顺序仲裁：

1. **不做项 / 范围边界**
2. **当前仓库实际文件**（仅限包路径、启动类、依赖版本、已有代码现状）
3. **`device_management.sql`**（角色、审批模型、表结构、字段、索引、枚举、种子数据、通知模型、统计模型）
4. **API 文档与前端项目目录结构**（接口路径、HTTP 方法、分页/筛选/鉴权/联调承载）
5. **其余系统设计文字说明与图示**

> 说明：当前外部资料存在“旧两角色 / 旧单审批 / 旧通知模型”和“SQL 新口径”并存现象。凡涉及实现基线的核心契约，一律优先采用 SQL 新口径。

### 02-3 SQL 优先仲裁项

以下内容若与旧文档冲突，统一以 `device_management.sql` 为准：

- 角色体系为 `USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN` 三角色，而非旧文档中的 `USER` / `ADMIN`
- 预约支持 `DEVICE_ONLY` 与 `DEVICE_THEN_SYSTEM` 两种审批模式
- 预约状态为 `PENDING_DEVICE_APPROVAL` / `PENDING_SYSTEM_APPROVAL` / `PENDING_MANUAL` / `APPROVED` / `REJECTED` / `CANCELLED` / `EXPIRED`
- 存在正式表 `reservation_batch`，批量预约不是临时扩展功能
- `notification_record` 已包含 `channel`、`read_flag`、`read_at`、`template_vars`、`retry_count`、`related_id`、`related_type`
- 通知渠道为 `IN_APP` / `EMAIL` / `SMS`，其中已读能力仅对 `IN_APP` 生效
- 统计模型使用 `stat_type` / `granularity` / `subject_type` / `subject_value`

### 02-4 数据库与接口真相源

- **数据库结构** 以 `device_management.sql` 为准，尤其是：表名、字段名、默认值、索引、外键、种子数据、枚举注释
- **主键类型** 统一为 `VARCHAR(36)` / Java `String` UUID，严禁在代码、测试、计划或文档中使用 `Long` / `BIGINT` 作为主键假设
- **接口协议** 统一 `/api` 前缀、统一 Bearer Token、统一 JSON 响应；HTTP 方法按 API 文档与前端承载执行，现有明确包含 `GET` / `POST` / `PUT` / `DELETE`
- **文件上传** 统一按 `multipart/form-data` 处理
- **术语与状态** 必须遵循分域状态集，禁止脱离业务域解释 `PENDING`、`BORROWED`、`FAILED` 等同码状态

### 02-5 文档同步要求

出现以下变更时，必须同步更新相关文档：

- 数据库表结构、字段、索引、约束、枚举值变更
- 接口路径、HTTP 方法、请求体、响应体、权限要求变更
- 业务状态流转、时间窗口、定时任务编号或 Cron 变更
- 前后端联动页面的接口承载方式变更
- SQL 新口径与旧设计文档之间的冲突仲裁结果变更

若发现资料冲突且无法直接裁决，统一使用以下格式记录：

> [!question]+ 待业务确认（含位置与原因）
> - **冲突位置**: 章节或文件位置
> - **冲突原因**: 具体矛盾点
> - **相关条目**: 相关需求 / API / SQL / 页面
> - **建议方案**: 候选处理方案

---

## 03 Git 分支管理

### 03-1 核心原则

- 禁止直接在 `main` 分支开发，所有开发任务必须在功能分支完成
- 默认按功能闭环提交，避免“半个模块”长时间悬挂
- 未经用户明确要求，不主动执行提交、合并、推送等 Git 写操作
- 若目标文件已存在未提交本地修改，优先在现有修改基础上增量调整，不得直接覆盖用户内容

### 03-2 分支命名规范

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

### 03-3 本地开发流程

```bash
# 1) 基于 main 创建新分支
git checkout main
git pull
git checkout -b feature/xxx

# 2) 开发并提交
git add .
git commit -m "feat(auth): 新增 xxx 功能"

# 3) 合并前必须通过构建和测试
mvn clean verify
```

### 03-4 提交信息规范

```text
<type>(<scope>): <subject>
```

常用类型：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`。

---

## 04 项目定位与范围边界

### 04-1 项目目标

本项目是“智能设备管理系统”后端，核心覆盖以下业务域：

- 用户与权限管理
- 设备与设备分类管理
- 预约管理
- 借还管理
- 逾期治理
- 智能对话预约
- 统计分析
- 消息通知与定时任务支撑

### 04-2 本期不做项

以下内容默认不在本期范围内：

- 移动端 App / 微信小程序
- 微信通知、日历同步、开放平台、多语言
- 独立维修工单、财务结算、Excel / PDF 文件导出
- 语音正式能力（仅预留扩展入口，不实现 ASR / TTS）
- RAG、联网检索、多模型编排、Agent 自主决策、图像识别
- 开放式库存系统、财务结算、推荐系统、长期记忆

### 04-3 AI 能力边界

- 本期 AI 以**文本对话**为主，语音只保留扩展入口
- AI 只能通过 Service 层调用业务能力，不得直接写数据库
- AI 意图固定为 `RESERVE` / `QUERY` / `CANCEL` / `HELP` / `UNKNOWN`
- Prompt 模板是正式模块；`DialogConfig` 当前无稳定数据模型，不纳入本期实现范围

---

## 05 工程现状与目标基线

### 05-1 当前仓库现状

- 当前启动类为 `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`
- 当前包根为 `com.jhun.backend`
- 当前 `pom.xml` 仍是 Spring Boot `4.0.3` 的最小初始化骨架
- 当前仓库尚未落地 `config/common/dto/entity/mapper/service/controller/scheduler` 目标结构

### 05-2 目标工程基线

| 技术 | 版本基线 | 说明 |
|------|----------|------|
| Java | 21 | 语言与运行时 |
| Maven | 3.x | 构建与依赖管理 |
| Spring Boot | 4.x | 目标实现基线 |
| Spring Security | 6.x | 认证与鉴权 |
| MyBatis-Plus | Boot 4 兼容版本 | 数据访问 |
| MySQL | 8.x | 关系型数据库 |
| Redis | Latest | Token / 会话 / 验证码缓存 |
| JWT | Latest | Access Token / Refresh Token |
| Spring AI | Latest | LLM 接入层 |
| SMTP | - | 邮件通知 |

> 强约束：除非用户明确要求，不主动重命名启动类或迁移包根；后续所有新增目录默认继续落在 `src/main/java/com/jhun/backend/` 下。

### 05-3 分层与目录约定

项目按以下结构组织：

```text
src/main/java/com/jhun/backend/
├── config/
├── common/
├── dto/
├── entity/
├── mapper/
├── service/
├── controller/
├── scheduler/
└── util/
```

核心约定：

- 采用三层架构：`Controller -> Service -> Mapper`
- 使用 DTO 隔离接口输入输出，不直接暴露 Entity
- 统一响应放在 `common/response/`，统一异常放在 `common/exception/`
- `service/support/` 用于放置校验器、检测器、通知装配器、LLM 客户端等辅助组件
- `scheduler/` 必须按业务域拆分：`reservation/`、`overdue/`、`statistics/`、`system/`
- MyBatis XML 放在 `src/main/resources/mapper/`
- 邮件模板放在 `src/main/resources/templates/email/`
- Prompt 模板放在 `src/main/resources/templates/ai/`
- 初始化 SQL 放在 `src/main/resources/sql/`
- 测试分为 `src/test/java/com/jhun/backend/unit/` 与 `src/test/java/com/jhun/backend/integration/`

---

## 06 角色、审批模型与模块边界

### 06-1 固定角色边界

系统固定且互斥 3 类角色：

| 角色 | 说明 | 关键边界 |
|------|------|----------|
| `USER` | 普通用户 | 浏览设备、提交本人预约、发起本人批量预约、签到、取消本人预约、查看本人记录、使用 AI 文本对话 |
| `DEVICE_ADMIN` | 设备管理员 | 管理设备生命周期、执行预约第一审、确认借用与归还、处理逾期 |
| `SYSTEM_ADMIN` | 系统管理员 | 管理用户与角色权限、冻结/解冻用户、执行预约第二审、代预约、管理型批量预约、统计分析、Prompt 模板 |

强约束：

- 注册用户默认绑定 `USER`
- 不允许引入第四种业务角色类型
- `DEVICE_ADMIN` 不得提交本人预约
- `SYSTEM_ADMIN` 不参与借用确认、归还确认和逾期处理
- `SYSTEM_ADMIN` 仅可代 `USER` 预约，不得代 `DEVICE_ADMIN` 或 `SYSTEM_ADMIN` 预约

### 06-2 审批与预约模型

- 审批模式固定为 `DEVICE_ONLY` / `DEVICE_THEN_SYSTEM`
- 设备分类可配置默认审批模式，设备可配置覆盖审批模式
- 预约表必须保存 `approval_mode_snapshot`
- 第一审仅允许 `DEVICE_ADMIN` 执行
- 第二审仅允许 `SYSTEM_ADMIN` 执行
- 同一账号不得完成双审两步，该规则必须在应用层强校验
- 预约模式固定为 `SELF` / `ON_BEHALF`
- 批量预约是正式能力，对应表 `reservation_batch`

### 06-3 必备模块与建议接口前缀

| 模块 | Controller | 建议接口前缀 | 核心实体 / 记录 |
|------|------------|--------------|------------------|
| 认证与账户 | `AuthController` | `/api/auth` | `User`、`PasswordHistory` |
| 用户管理 | `UserController` | `/api/admin/users` | `User` |
| 角色权限 | `RoleController` | `/api/admin/roles` | `Role`、`Permission`、`RolePermission` |
| 设备管理 | `DeviceController` | `/api/devices` | `Device`、`DeviceStatusLog` |
| 设备分类 | `DeviceCategoryController` | `/api/device-categories` | `DeviceCategory` |
| 预约管理 | `ReservationController` | `/api/reservations` | `Reservation` |
| 预约批次 | `ReservationBatchController` | `/api/reservation-batches` | `ReservationBatch` |
| 借还管理 | `BorrowController` | `/api/borrow-records` | `BorrowRecord` |
| 逾期管理 | `OverdueController` | `/api/overdue-records` | `OverdueRecord` |
| 通知管理 | `NotificationController` | `/api/notifications` | `NotificationRecord` |
| AI 对话 | `AiController` | `/api/ai` | `ChatHistory` |
| Prompt 模板 | `PromptTemplateController` | `/api/ai/prompts*` | `PromptTemplate` |
| 统计分析 | `StatisticsController` | `/api/statistics` | `StatisticsDaily` |

---

## 07 核心数据模型与枚举契约

### 07-1 核心表清单

数据库核心表至少包括以下 16 张：

- `role`
- `permission`
- `user`
- `role_permission`
- `password_history`
- `device_category`
- `device`
- `device_status_log`
- `reservation_batch`
- `reservation`
- `borrow_record`
- `overdue_record`
- `notification_record`
- `chat_history`
- `prompt_template`
- `statistics_daily`

### 07-2 必备枚举集合

至少显式维护以下枚举或等价常量：

- `RoleCode`: `USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN`
- `DeviceStatus`: `AVAILABLE` / `BORROWED` / `MAINTENANCE` / `DISABLED` / `DELETED`
- `ApprovalMode`: `DEVICE_ONLY` / `DEVICE_THEN_SYSTEM`
- `ReservationMode`: `SELF` / `ON_BEHALF`
- `ReservationStatus`: `PENDING_DEVICE_APPROVAL` / `PENDING_SYSTEM_APPROVAL` / `PENDING_MANUAL` / `APPROVED` / `REJECTED` / `CANCELLED` / `EXPIRED`
- `ReservationBatchStatus`: `PROCESSING` / `SUCCESS` / `PARTIAL_SUCCESS` / `FAILED` / `CANCELLED`
- `CheckInStatus`: `NOT_CHECKED_IN` / `CHECKED_IN` / `CHECKED_IN_TIMEOUT`
- `BorrowStatus`: `BORROWED` / `RETURNED` / `OVERDUE`
- `OverdueProcessingStatus`: `PENDING` / `PROCESSED`
- `OverdueHandleType`: `WARNING` / `COMPENSATION` / `CONTINUE`
- `FreezeStatus`: `NORMAL` / `RESTRICTED` / `FROZEN`
- `NotificationType`: `VERIFY_CODE` / `FIRST_APPROVAL_TODO` / `SECOND_APPROVAL_TODO` / `APPROVAL_PASSED` / `APPROVAL_REJECTED` / `APPROVAL_EXPIRED` / `RESERVATION_REMINDER` / `CHECKIN_TIMEOUT_WARNING` / `BORROW_CONFIRM_WARNING` / `OVERDUE_WARNING` / `REVIEW_TIMEOUT_WARNING` / `RESERVATION_CANCELLED` / `BATCH_RESERVATION_RESULT` / `ON_BEHALF_CREATED` / `PENDING_MANUAL_NOTICE` / `ACCOUNT_FREEZE_UNFREEZE` / `DEVICE_MAINTENANCE_NOTICE`
- `NotificationChannel`: `IN_APP` / `EMAIL` / `SMS`
- `NotificationStatus`: `PENDING` / `SENDING` / `SUCCESS` / `FAILED`
- `AiIntentType`: `RESERVE` / `QUERY` / `CANCEL` / `HELP` / `UNKNOWN`
- `PromptTemplateType`: `INTENT_RECOGNITION` / `INFO_EXTRACTION` / `RESULT_FEEDBACK` / `CONFLICT_RECOMMENDATION`
- `StatisticsType`: `DEVICE_UTILIZATION` / `CATEGORY_UTILIZATION` / `USER_BORROW` / `TIME_DISTRIBUTION` / `OVERDUE_STAT`
- `StatisticsGranularity`: `HOUR` / `DAY` / `WEEK` / `MONTH`
- `StatisticsSubjectType`: `GLOBAL` / `DEVICE` / `USER` / `CATEGORY` / `TIME_SLOT`

### 07-3 分域状态解释

必须使用“状态集名 + 状态码”解释状态：

- `reservation_status.PENDING_DEVICE_APPROVAL` = 待设备审批
- `reservation_status.PENDING_SYSTEM_APPROVAL` = 待系统审批
- `reservation_status.PENDING_MANUAL` = 待人工处理
- `borrow_status.BORROWED` = 借用中（借还域）
- `device_status.BORROWED` = 已借出（设备域）
- `notification_status.PENDING` = 待发送（通知域）

### 07-4 历史口径警告

以下旧口径不得继续写入新代码、新测试和新文档：

- `ADMIN` 单一管理员角色
- 预约状态 `PENDING`
- 通知渠道仅 `EMAIL` / `SMS`
- 通知状态 `SENT`
- 省略 `reservation_batch` 或 `password_history`

---

## 08 关键业务规则与定时任务

### 08-1 预约、审批与签到规则

- 预约开放时间默认 `08:00-22:00`
- 最短预约时长 30 分钟，最长 7 天
- `USER` 可本人单条预约与本人批量预约，`SYSTEM_ADMIN` 可代 `USER` 预约并可发起管理型批量预约，`DEVICE_ADMIN` 不可创建预约
- 审批模式为 `DEVICE_ONLY` 时，预约初始状态进入 `PENDING_DEVICE_APPROVAL`
- 审批模式为 `DEVICE_THEN_SYSTEM` 时，预约先经第一审，再进入 `PENDING_SYSTEM_APPROVAL`
- 开始前超过 24 小时，用户可自行取消
- 开始前 24 小时内到开始前，取消需管理员处理
- 开始后不可取消
- 签到窗口：开始前 30 分钟至开始后 30 分钟为正常签到；开始后 30 至 60 分钟为超时签到；超过 60 分钟未签到则预约过期
- 用户签到后，设备管理员须在 2 小时内确认借用；超时进入 `PENDING_MANUAL`
- 进入 `PENDING_MANUAL` 后 24 小时仍未处理，自动取消预约

### 08-2 借还与设备状态规则

- 只有 `DEVICE_ADMIN` 可确认借用与归还
- 借用确认后生成 `borrow_record`
- 归还确认后设备状态才可从 `BORROWED` 回到 `AVAILABLE`
- 禁止通过手工更新绕过 `BORROWED -> AVAILABLE` 的归还流程
- 设备进入 `DELETED` 后视为业务终态，历史记录必须保留

### 08-3 逾期与冻结规则

- 以 `borrow_record.expected_return_time` 作为逾期计算基准
- 逾期 `< 4h`：预警但不记正式逾期天数
- 逾期 `4h ~ 96h`：用户进入 `RESTRICTED`
- 逾期 `>= 96h`：用户进入 `FROZEN`
- `RESTRICTED` 可由系统自动解除；`FROZEN` 仅允许 `SYSTEM_ADMIN` 解除
- 逾期处理记录需保留处理人、处理方式、备注、赔偿金额、通知状态

### 08-4 通知规则

- `IN_APP` 为正式通知渠道，支持已读 / 未读
- `EMAIL` 为正式发送渠道，`SMS` 为预留扩展渠道
- 通知记录必须保存 `channel`、`template_vars`、`status`、`retry_count`、`error_message`、`related_id`、`related_type`
- 批量预约、代预约、审批流转、签到/借用超时、逾期、冻结/解冻、设备维修都应产生对应通知

### 08-5 定时任务编号

| 编号 | 任务 | Cron | 说明 |
|------|------|------|------|
| `C-01` | `ReservationAuditTimeoutReminder` | `0 0 * * * ?` | 48 小时审核超时提醒 |
| `C-02` | `ReservationAutoExpireProcessor` | `0 15 * * * ?` | 72 小时未审核自动过期 |
| `C-03` | `ReservationCheckInTimeoutProcessor` | `0 */15 * * * ?` | 签到超时处理 |
| `C-04` | `BorrowConfirmTimeoutProcessor` | `0 */15 * * * ?` | 借用确认超时转人工处理 |
| `C-05` | `OverdueAutoDetectProcessor` | `0 0 * * * ?` | 逾期自动识别 |
| `C-06` | `OverdueNotificationProcessor` | `0 30 * * * ?` | 逾期通知发送 |
| `C-07` | `OverdueRestrictionReleaseProcessor` | `0 0 2 * * ?` | 限制自动解除 |
| `C-08` | `StatisticsAggregationProcessor` | `0 30 2 * * ?` | T+1 统计预聚合 |
| `C-09` | `TokenCleanupProcessor` | `0 0 3 * * ?` | Token 清理 |
| `C-10` | `SessionTimeoutProcessor` | `0 */10 * * * ?` | 会话空闲超时检查 |
| `C-11` | `ReservationUpcomingReminder` | `0 */15 * * * ?` | 预约开始前 30 分钟提醒 |

---

## 09 与前端联动约定

### 09-1 重点接口承载

后端设计必须主动对齐前端以下模块：

- `/api/auth/*`
- `/api/admin/users/*`
- `/api/admin/roles/*`
- `/api/devices/*`
- `/api/device-categories/*`
- `/api/reservations/*`
- `/api/reservation-batches/*`
- `/api/borrow-records/*`
- `/api/overdue-records/*`
- `/api/notifications/*`
- `/api/ai/*`
- `/api/ai/prompts*`
- `/api/statistics/*`

### 09-2 当前联动说明

- 前端目录稿已存在 `notifications` 模块与 `markAsRead` / `unreadCount` 承载，后端实现计划必须纳入通知查询与已读接口
- API 附录当前仍缺少独立通知接口定义，落地实现时必须把通知接口同步回写到文档
- Prompt 模板后端接口统一按 `/api/ai/prompts*`，不要误用前端页面路由 `/admin/prompt-templates`
- 角色接口继续沿用 `/api/admin/roles/*`，即使旧 API 示例中仍存在 `ADMIN` 角色口径，也要按三角色实现

---

## 10 构建、测试与质量门禁

### 10-1 Maven 常用命令

| 命令 | 说明 |
|------|------|
| `mvn clean compile` | 清理并编译 |
| `mvn clean test` | 运行全部测试 |
| `mvn clean package` | 打包（默认跑测试） |
| `mvn clean package -DskipTests` | 跳过测试打包 |
| `mvn clean verify` | 执行完整校验流程 |
| `mvn spring-boot:run` | 本地启动服务 |

### 10-2 测试层次要求

- 单元测试：校验业务规则、状态机、校验器、工具类、AI 解析器
- 集成测试：覆盖 Controller、鉴权链、事务、Mapper 与定时任务
- 核心链路必须覆盖成功与失败路径：
  - 注册 / 登录 / Token 刷新 / 密码重置 / 个人信息修改
  - 用户状态更新 / 角色分配 / 冻结解冻
  - 预约冲突检测 / 一审 / 二审 / 代预约 / 批量预约 / 签到 / 人工处理
  - 借用确认 / 归还确认 / 设备状态联动
  - 逾期识别 / 限制解除 / 通知发送 / 通知已读
  - AI 调用与降级
  - 统计预聚合与排名查询

### 10-3 质量门禁

- 每完成一个阶段或模块，必须做代码审查
- 准备宣称“完成”前，必须执行验证命令并核对结果
- 若变更涉及 schema、接口或业务规则，必须同步更新文档后才可视为完成

---

## 11 命名规范

### 11-1 类与接口命名

| 类型 | 命名规范 | 示例 |
|------|----------|------|
| 实体类 | 名词，PascalCase | `User`、`Device` |
| Service 接口 | 名词 + `Service` | `UserService` |
| Service 实现 | 接口名 + `Impl` | `UserServiceImpl` |
| Controller | 名词 + `Controller` | `DeviceController` |
| Mapper | 实体名 + `Mapper` | `ReservationMapper` |
| 请求 DTO | 动作 + 实体 + `Request` | `CreateReservationRequest` |
| 响应 DTO | 实体 + `Response` | `BorrowResponse` |
| 工具类 | 名词 + `Util` | `JwtUtil` |
| 常量类 | 名词 + `Constants` | `RedisKeyConstants` |

### 11-2 模块术语规范

统一使用以下业务术语：

- 用户与权限管理
- 设备管理
- 预约管理
- 预约批次 / 批量预约
- 借还管理
- 逾期管理
- 智能对话预约（预留语音扩展）
- 统计分析
- 消息通知

---

## 12 Agent 执行提醒

- 开发前优先核对当前仓库状态与文档真相源，不要只根据旧计划文件推断实现细节
- 发现包名、文件名、表结构、状态枚举不一致时，先比对 SQL 与系统设计，再决定是否修文档或修代码
- 对于 `String` UUID、固定三角色、审批模式、预约状态、通知类型、通知渠道、定时任务编号，不要擅自改口径
- 若旧文档仍出现 `ADMIN`、`PENDING`、`SENT` 等历史口径，默认视为待同步旧内容，不作为新实现基线
- 如果为了支持前端页面新增接口、字段或枚举，必须同时说明影响到哪些页面、哪些后端模块、哪些文档
- 若开始实际编码，优先先把 `pom.xml`、配置骨架、安全基线和 SQL 脚本对齐，再进入业务模块开发
