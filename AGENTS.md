# AGENTS.md - 设备管理后端项目

本文档用于指导本仓库内的 Agentic Coding Agents 统一开展后端设计、开发、测试、联调与文档维护工作。

---

## 01 语言设置

- **回答语言**: 中文（简体）
- 所有 AI 文档、说明统一使用中文（简体）
- 项目中的代码、脚本、SQL、配置与模板都必须纳入中文注释治理范围；对新增、修改、维护时触达的业务规则、接口契约、状态语义、关键流程与复杂逻辑，必须补齐与复杂度匹配的中文（简体）注释，且必须根据代码内容选择匹配的注释格式
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

## 11 代码注释规范

### 11-1 总体要求

- **适用范围**: 项目中的 Java 主代码、测试代码、Mapper XML、SQL 脚本、YAML / properties 配置、Shell / Batch / PowerShell 脚本、模板文件、初始化数据脚本、测试数据脚本都必须纳入中文注释治理范围
- **强制覆盖要求**: 项目中的代码都必须纳入中文注释治理；对本次新增或修改范围内的类、接口、枚举、记录类型、公共方法、关键字段、复杂逻辑块、SQL 语句块、配置块、测试场景块以及同文件内与本次改动直接关联的关键声明，必须补齐与复杂度匹配的中文注释，不得以“后续再补”为由留空
- **语言要求**: 所有新增注释、补充注释与修订注释统一使用中文（简体），术语必须与本文件、`device_management.sql`、系统设计文档保持一致
- **目标要求**: 注释必须解释“为什么这样写、对应什么业务规则、边界条件是什么、会产生哪些副作用或维护风险”，禁止把代码字面意思机械翻译成注释
- **格式要求**: 注释格式必须随代码内容变化而变化；Javadoc、字段说明、块注释、行内提示、XML 注释、SQL 注释、配置块注释应按场景优先选择，禁止所有代码统一套用同一种注释格式
- **同步要求**: 修改代码逻辑、SQL 条件、配置含义、测试断言、脚本行为或模板占位语义时，必须同步修改对应注释；代码与注释不一致视为未完成
- **完整要求**: “完整详细中文注释”是指所有具备业务含义、状态约束、权限边界、时间窗口、事务副作用、并发风险或理解门槛的实现都必须有匹配层级的注释，不要求对显而易见的样板代码做逐行翻译式注释
- **真相源要求**: 注释内容不得与真相源冲突，尤其不得违反三角色模型、审批模式、预约状态、通知模型、定时任务编号、主键 UUID 等核心契约

### 11-2 注释格式选择规则

| 场景 | 优先格式 | 说明 |
|------|----------|------|
| Java 类 / 接口 / 抽象类 / 枚举 / `record` | `/** ... */` Javadoc | 说明职责、业务域、分层位置、关键依赖、边界约束 |
| Java 公共方法 / 构造器 / 复杂私有方法 | `/** ... */` Javadoc | 说明用途、参数、返回值、副作用、异常路径、权限或事务约束 |
| Java 字段 / 常量 / 状态字段 / 注入依赖 | 字段上方说明注释，优先 `/** ... */` | 说明字段业务语义、适用范围、禁止误用场景 |
| 复杂分支 / 状态机 / 时间窗口 / 并发控制 / 补偿逻辑 | 块级注释 `/* ... */` 或连续行注释 `//` | 解释规则来源、判断原因、边界条件、顺序依赖 |
| 测试类 / 测试方法 | `/** ... */` Javadoc 或等价块注释 | 说明场景前提、测试意图、预期结果、要防止的回归风险 |
| 测试数据构造 / 断言保护点 | 邻近块注释或行注释 | 说明数据为什么这样造、断言在保护哪条业务契约 |
| Mapper XML 语句 / 动态条件 | `<!-- ... -->` | 放在语句或条件上方，说明业务场景、筛选目的、输出用途 |
| SQL 建表 / 索引 / 种子数据 / 迁移脚本 | `--` 或 `/* ... */` | 说明对象职责、索引意图、兼容性影响、风险点 |
| YAML / properties / 脚本 / 模板 | `#`、`<!-- ... -->` 或语言原生注释 | 在原生格式支持且不会污染运行结果时，说明配置块、脚本步骤、模板占位符、环境差异 |

补充约束：

- **`record` / 精简 DTO**: 若语言结构不便对每个字段分别写注释，必须在类型注释中逐项列出关键字段的业务含义、取值约束与使用边界
- **单行注释使用场景**: 单行注释只用于提示局部规则、临界条件与风险点，不得替代本应存在的类级或方法级说明
- **格式一致性**: 同一文件内的同类内容应尽量使用一致格式，但不得因为追求整齐而牺牲信息量
- **例外处理规则**: 自动生成文件、第三方引入文件、天然不支持注释的格式，以及注释会直接改变语义或输出结果的模板/数据文件，可通过同目录或上一级目录中可直接定位的 README、配套设计文档、脚本说明文件或文件头外层文档补充说明，但必须保证维护者能快速定位到规则来源

### 11-3 Java 主代码注释规范

- **类 / 接口 / 抽象类注释**: 对具备业务职责或理解门槛的实现，必须说明职责、所属业务域、在分层架构中的位置、依赖的上下游组件、权限与事务边界；例如 Controller 说明接口承载职责，Service 说明业务编排职责，Mapper 说明数据访问职责
- **Controller 注释**: 类注释必须说明承载的接口范围与目标调用方；方法注释必须说明接口用途、角色要求、请求参数约束、响应语义、异常路径与前后端联调注意事项
- **Service / ServiceImpl 注释**: 必须说明业务编排原因、状态流转依据、事务边界、幂等要求、通知副作用、与其他服务或 Mapper 的协作关系
- **Entity / DTO / VO / `record` 注释**: 必须说明对象在业务中的定位；业务字段、状态字段、时间字段、快照字段、外键字段与布尔控制字段必须解释字段语义、取值来源与禁止误用场景
- **枚举 / 常量注释**: 必须说明状态码、枚举值或常量的业务含义、适用业务域、转换规则与禁止混用范围，避免不同业务域同码异义被混用
- **Mapper 接口注释**: 必须说明每个方法对应的查询/更新意图、输入条件、输出对象、事务语义与使用限制，避免调用方误解语句副作用
- **方法注释**: 公共方法必须说明用途、参数含义、返回结果、副作用、异常路径、权限/事务/幂等约束；承载关键业务判断的私有方法也必须说明规则来源与输出意义
- **逻辑块注释**: 对复杂条件分支、审批链路、状态流转、时间窗口计算、冲突检测、兼容处理、补偿回滚、缓存一致性、并发锁控制等非直观逻辑，必须使用块级注释说明处理原因与业务依据
- **基础设施与工具代码注释**: 安全配置、JWT 解析、异常处理、统一响应、文件存储、邮件发送、定时任务、工具类都必须说明使用边界、失败后果、降级策略与不可绕过的前置条件
- **重点强制注释项**: 涉及 `USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN` 角色边界、`DEVICE_ONLY` / `DEVICE_THEN_SYSTEM` 审批模型、预约状态流转、借还联动、逾期冻结、通知发送、定时任务执行条件的代码，必须写详细中文注释

### 11-4 测试代码注释规范

- **测试类注释**: 必须说明被测对象、覆盖范围与测试目标，是验证业务规则、接口行为、鉴权链路、事务一致性还是集成流程
- **测试方法注释**: 必须说明场景前提、执行动作、预期结果与对应风险点，不能只写“测试成功”或“测试失败”这类空泛描述
- **测试结构注释**: 推荐按 Arrange / Act / Assert 或“准备 / 执行 / 断言”组织注释；当测试步骤较长或包含多个业务阶段时，必须在关键阶段前加块注释帮助定位
- **测试数据注释**: 对预约时间窗、审批顺序、逾期时长、冻结阈值、权限身份、状态切换、边界日期、随机数种子等具有业务含义的测试数据，必须说明为什么这样构造以及它映射哪条业务规则
- **断言注释**: 当断言不是显而易见时，必须说明断言在保护什么业务契约、边界行为或回归风险，避免后续维护时误删关键断言
- **辅助方法与清理逻辑注释**: 测试中的建数方法、Mock 装配、上下文初始化、数据库清理、时钟控制、并发工具装配都必须说明复用目的与隐含前置条件
- **异常与边界场景**: 权限拒绝、状态非法流转、参数越界、空数据、重复提交、超时处理、回滚校验等测试必须写明场景价值，避免后续维护时误删关键用例

### 11-5 Mapper XML 与 SQL 注释规范

- **Mapper 语句注释**: 每个核心查询、更新、删除、聚合、批处理语句都必须在语句上方说明用途、输入条件、输出对象、影响范围以及对应业务场景
- **动态条件注释**: XML 中的动态 `if`、`choose`、`where`、`trim`、`set` 条件必须说明触发条件、筛选目的、空值语义与业务约束，避免维护时误改过滤逻辑
- **关联与映射注释**: 多表关联、结果映射、嵌套查询、分页排序、聚合统计必须说明字段来源、关联关系、排序依据、聚合口径与结果用途
- **SQL 脚本注释**: 建表、索引、初始化数据、迁移脚本、回填脚本必须说明对象职责、字段语义、索引意图、种子数据用途、兼容性影响与风险点
- **性能与一致性注释**: 涉及索引命中、批量更新、统计聚合、逻辑删除、定时扫描、锁竞争、长事务风险、幂等回放的 SQL，必须补充性能或一致性说明
- **种子数据与测试数据注释**: 角色、权限、默认分类、测试用户、测试设备等初始化数据必须说明用途、适用环境与删除/复用注意事项
- **术语约束**: SQL 与 XML 注释中必须使用标准业务口径，不得继续使用 `ADMIN`、`PENDING`、`SENT` 等历史旧口径

### 11-6 配置、脚本与模板注释规范

- **配置文件注释策略**: `application*.yml`、`application.properties` 及其他配置文件采用“配置块说明 + 关键字段说明”模式，不要求逐行翻译，但关键配置块与关键字段必须做到可读、可维护、可追溯
- **必注释配置块**: 数据源、MyBatis / MyBatis-Plus、Spring Security、Redis、JWT、SMTP、AI、定时任务、日志、文件上传、跨域、安全白名单等关键配置必须说明用途、默认值影响与联动关系
- **脚本注释要求**: Shell、Batch、PowerShell、初始化脚本、清理脚本、发布脚本必须说明执行目的、输入参数、依赖环境、危险步骤、失败回滚方式与禁止直接运行的场景
- **模板注释要求**: 邮件模板、AI Prompt 模板、占位文本模板在原生格式支持且不会污染渲染或推理结果时，必须说明模板用途、主要占位变量、变量来源、触发场景与不允许删除的占位符；若模板内容不适合内嵌注释，必须在邻近文档中补充说明
- **环境差异说明**: 涉及本地、测试、生产环境差异的配置和脚本，必须说明覆盖方式、敏感性、部署注意事项与常见错误后果
- **敏感信息约束**: 注释可以说明配置用途与风险，但不得在注释中泄露真实密钥、令牌、密码、邮箱授权码、连接串敏感片段等安全信息

### 11-7 注释禁止事项

- 禁止编写纯粹复述代码表面的机械注释，例如“给变量赋值”“遍历列表”“调用查询方法”这类无信息增量内容
- 禁止不区分场景统一套用同一种注释格式，例如整份文件只写行注释、只写 Javadoc 或只写文件头注释却不解释关键逻辑
- 禁止使用与当前真相源冲突的旧术语、旧状态、旧角色口径编写注释
- 禁止代码逻辑已变更但注释未同步更新，导致代码与注释相互矛盾
- 禁止在注释中写入未经确认的业务假设、与实际行为不符的描述或未来不确定计划并伪装成既定规则
- 禁止在注释中泄露账号密码、Token、密钥、连接串敏感片段或其他安全信息
- 禁止为了追求“注释覆盖率”而堆砌无意义注释，造成真正关键规则被噪声淹没
- 禁止在触达文件时遗漏本次改动涉及逻辑及其直接关联规则所需的注释，或用 `TODO`、`后续补充`、`待完善` 长期替代正式说明

### 11-8 评审与验收要求

- **完成标准**: 新增或修改代码、SQL、XML、配置、脚本、模板时，若本次改动涉及的业务规则、关键流程、复杂逻辑或关联声明缺少与实现复杂度匹配的中文注释，或注释格式明显与内容类型不匹配，视为任务未完成
- **评审重点**: 代码审查不仅检查“有没有注释”，还必须检查“注释是否解释了为什么这样做、业务依据是什么、边界条件是什么、副作用和风险点是什么”
- **重点审查范围**: 业务规则、状态机、审批流、权限控制、事务边界、并发控制、定时任务、SQL 条件、统计逻辑、外部集成、异常恢复相关改动必须重点审查注释质量
- **文档联动**: 若变更同时影响 schema、接口、状态流转、调度规则、配置项或模板变量，除更新文档外，还必须同步更新代码注释、SQL 注释、配置说明与模板注释
- **存量治理要求**: 本项目中的存量代码也应按本规范逐步补齐注释；当维护者触达历史代码并进行修改时，应至少完成本次修改范围及其直接关联逻辑的注释补齐或修正，不得只改逻辑不改注释
- **抽检要求**: 评审时应至少抽查类级注释、方法注释、关键逻辑块注释、测试场景注释与配置块注释，确认项目未出现“只有文件头有注释、正文无解释”的伪合规情况
- **Agent 执行要求**: Agent 在实施开发、修复缺陷、补测试、调整 SQL、修改配置、维护脚本、更新模板时，必须把补充和校准中文注释作为交付内容的一部分，不得省略

---

## 12 命名规范

### 12-1 类与接口命名

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

### 12-2 模块术语规范

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

## 13 Agent 执行提醒

- 开发前优先核对当前仓库状态与文档真相源，不要只根据旧计划文件推断实现细节
- 发现包名、文件名、表结构、状态枚举不一致时，先比对 SQL 与系统设计，再决定是否修文档或修代码
- 对于 `String` UUID、固定三角色、审批模式、预约状态、通知类型、通知渠道、定时任务编号，不要擅自改口径
- 若旧文档仍出现 `ADMIN`、`PENDING`、`SENT` 等历史口径，默认视为待同步旧内容，不作为新实现基线
- 如果为了支持前端页面新增接口、字段或枚举，必须同时说明影响到哪些页面、哪些后端模块、哪些文档
- 任何新增或修改的代码、脚本、SQL、配置、模板、测试都必须按第 11 章同步补齐本次改动范围内所需的中文注释；未补齐前不得宣称完成
- 若开始实际编码，优先先把 `pom.xml`、配置骨架、安全基线和 SQL 脚本对齐，再进入业务模块开发
