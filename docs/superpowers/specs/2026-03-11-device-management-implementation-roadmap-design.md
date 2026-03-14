# 设备管理后端项目实施路线设计

## 1. 背景

当前仓库仍处于 Spring Boot 初始化骨架阶段，实际仅包含：

- 启动类 `src/main/java/com/jhun/backend/DeviceManagementBackendApplication.java`
- 上下文加载测试 `src/test/java/com/jhun/backend/DeviceManagementBackendApplicationTests.java`
- 最小化配置 `src/main/resources/application.properties`
- 初始 `pom.xml`

与此同时，外部资料已经给出了较完整的业务和工程目标：

- `系统功能设计/` 明确了业务范围、流程、非功能约束、定时任务、通知机制和验收标准
- `后端项目目录结构.md` 给出了目标分层与模块目录
- `前端项目目录结构.md` 给出了页面、状态管理和后端接口承载预期
- `device_management.sql` 给出了最新数据库 DDL、角色、审批模型、通知模型和统计模型

本次路线设计的目标不是泛化描述“后端开发顺序”，而是输出一份**同时对齐 SQL 新口径、前端联调承载、系统范围边界与当前仓库现实**的实施路线基线。

---

## 2. 真相源与仲裁基线

### 2.1 真相源清单

本设计以以下资料为准：

1. 系统功能设计系列文档
2. 后端项目目录结构文档
3. 前端项目目录结构文档
4. `device_management.sql`
5. 当前仓库实际文件结构（仅用于判断包路径、启动类、依赖现状与已有实现）

### 2.2 仲裁规则

当资料之间出现不一致时，按以下顺序仲裁：

1. **不做项 / 范围边界**
2. **当前仓库实际文件**（仅实现现状）
3. **`device_management.sql`**（角色、表结构、字段、状态机、通知、统计）
4. **API 文档与前端项目目录结构**（接口形态、HTTP 方法、联调承载）
5. **其余文字说明、图示与旧示例**

### 2.3 当前资料的关键分叉

当前资料存在明显“旧两角色 / 旧单审批 / 旧通知模型”和“SQL 新口径”并存现象，主要分叉如下：

| 主题 | 旧口径 | 新口径 / 最终基线 |
|------|--------|-------------------|
| 角色体系 | `USER` / `ADMIN` | `USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN` |
| 审批模型 | 单阶段待审核 | `DEVICE_ONLY` / `DEVICE_THEN_SYSTEM` |
| 预约状态 | `PENDING` | `PENDING_DEVICE_APPROVAL` / `PENDING_SYSTEM_APPROVAL` / `PENDING_MANUAL` |
| 预约批次 | 未显式建模 | `reservation_batch` 为正式表 |
| 通知渠道 | 邮件为主、短信预留 | `IN_APP` / `EMAIL` / `SMS` |
| 通知已读 | 被视为待补字段 | SQL 已有 `read_flag` / `read_at` |
| Prompt 接口前缀 | 易与后台页面路由混淆 | `/api/ai/prompts*` |
| 统计模型 | 旧维度型描述 | `stat_type` / `granularity` / `subject_type` / `subject_value` |

本路线设计明确采用 **SQL 新口径** 作为实施基线，并要求后续文档回写时把旧口径标记为历史内容。

---

## 3. 已确认的实施基线

### 3.1 仓库现状与工程基线

- 当前仓库实际包根保持 `com.jhun.backend`
- 当前启动类保持 `DeviceManagementBackendApplication`
- 当前 `pom.xml` 仍是 Spring Boot `4.0.3` 最小初始化骨架
- 目标工程基线调整为对齐当前仓库已采用的 Spring Boot `4.x`、Spring Security `6.x`、MyBatis-Plus、MySQL、Redis、JWT、Spring AI
- 后续实施默认**不迁移包根**，而是在 `com.jhun.backend` 下落地目标目录结构

### 3.2 数据与模型基线

- 主键统一采用 `String` UUID，对应 SQL 中 `VARCHAR(36)`
- `password_history` 是正式表，认证设计不得省略
- `reservation_batch` 是正式表，批量预约不是“以后再补”的增强项
- `reservation` 需要保留 `batch_id`、`created_by`、`reservation_mode`、`approval_mode_snapshot`、一审/二审审核字段
- `notification_record` 已正式包含 `channel`、`template_vars`、`status`、`retry_count`、`error_message`、`read_flag`、`read_at`、`related_id`、`related_type`
- `statistics_daily` 需要按 `stat_type`、`granularity`、`subject_type`、`subject_value` 建模与查询

### 3.3 角色与职责基线

- `USER`: 浏览设备、本人单条预约、本人批量预约、签到、查看本人记录、使用 AI 文本对话
- `DEVICE_ADMIN`: 设备生命周期管理、预约第一审、借用确认、归还确认、逾期处理
- `SYSTEM_ADMIN`: 用户与角色权限管理、冻结/解冻、预约第二审、代预约、管理型批量预约、统计、Prompt 模板
- `DEVICE_ADMIN` 不得创建预约
- `SYSTEM_ADMIN` 不参与借用确认、归还确认和逾期处理
- 同一账号不得完成双审两步，该规则必须在应用层强校验

### 3.4 接口与前端承载基线

- 后端统一使用 `/api` 前缀
- HTTP 方法以 API 文档和前端承载为准，现有明确包含 `GET` / `POST` / `PUT` / `DELETE`
- Prompt 模板接口统一按 `/api/ai/prompts*`
- 通知接口应形成正式家族 `/api/notifications*`，用于支撑前端通知页、未读数和已读动作
- 前端目录稿已经存在通知、AI 历史、角色权限、统计子页面承载，因此路线设计不能把这些能力无限后置

---

## 4. 关键设计结论

### 4.1 审批与预约设计

预约不再采用旧的“单一 `PENDING` 状态 + 管理员统审”模型，而是采用**审批模式驱动**：

- `DEVICE_ONLY`: 预约进入 `PENDING_DEVICE_APPROVAL`，由 `DEVICE_ADMIN` 审批，通过后转 `APPROVED`
- `DEVICE_THEN_SYSTEM`: 预约先进入 `PENDING_DEVICE_APPROVAL`，一审通过后转 `PENDING_SYSTEM_APPROVAL`，再由 `SYSTEM_ADMIN` 终审
- 用户签到后，如设备管理员未在 2 小时内确认借用，预约转 `PENDING_MANUAL`
- 取消、签到、借用确认、人工处理、逾期规则继续沿用系统设计中的时间窗口，但状态落点以 SQL 为准

这一变化意味着：认证授权、预约 DTO、查询接口、通知类型、定时任务筛选条件和测试用例都必须按新状态机重写，不能继续沿用旧的 `PENDING` 单态口径。

### 4.2 代预约与批量预约设计

`reservation_batch` 的存在说明系统已经把“批量预约”和“代预约”提升为正式业务能力，而非后台便捷操作。

- `reservation_mode` 固定为 `SELF` / `ON_BEHALF`
- `USER` 可以基于 `reservation_batch` 发起本人批量预约
- `created_by` 与 `user_id` 的区分必须保留，不能被 DTO 简化掉
- `SYSTEM_ADMIN` 可代 `USER` 创建预约并可发起管理型批量预约，但不得代 `DEVICE_ADMIN` 或 `SYSTEM_ADMIN` 创建预约
- 批量预约需要单独的批次实体、批次状态、结果汇总和通知机制
- 批量预约结果应触发 `BATCH_RESERVATION_RESULT` 通知

因此，实施路线中必须为 `ReservationBatch` 单独留出 DTO、Mapper、Service、Controller、测试和通知任务。

### 4.3 通知设计

通知域不再只是“邮件发送器”，而是一个包含站内信、发送状态、已读状态和业务关联的正式子系统。

- `IN_APP` 是正式通知渠道，不是前端自拟扩展
- `EMAIL` 是正式发送渠道，`SMS` 为预留扩展渠道
- 已读能力由 `read_flag` / `read_at` 支撑，仅对 `IN_APP` 生效
- 审批流、签到/借用超时、逾期、批量预约、代预约、冻结/解冻、设备维修都应映射到明确通知类型
- 前端通知页依赖至少 4 类接口：列表、未读数、单条已读、全部已读

> 结论：通知已读不再视为“待确认”，而是正式纳入实施范围；需要补的是 API 附录同步，而不是数据库设计。

### 4.4 AI 设计

- 本期 AI 以文本对话为主，语音仅保留扩展入口
- AI 只能通过 Service 层调度业务能力，不能直接写数据库
- AI 意图固定为 `RESERVE` / `QUERY` / `CANCEL` / `HELP` / `UNKNOWN`
- Prompt 模板管理是正式模块，对外接口按 `/api/ai/prompts*`
- `DialogConfig` 当前缺少稳定实体、SQL 和前端承载，不纳入本期实施范围

### 4.5 统计设计

统计域需要同时承接系统设计中的报表需求和 SQL 中的新聚合结构：

- 统计类型至少覆盖 `DEVICE_UTILIZATION`、`CATEGORY_UTILIZATION`、`USER_BORROW`、`TIME_DISTRIBUTION`、`OVERDUE_STAT`
- 聚合粒度至少覆盖 `HOUR` / `DAY` / `WEEK` / `MONTH`
- 统计对象类型至少覆盖 `GLOBAL` / `DEVICE` / `USER` / `CATEGORY` / `TIME_SLOT`
- 总览、设备利用率、借用统计、逾期统计、热门时段、设备排名、用户排名应作为正式交付项
- `T+1` 预聚合继续由定时任务 `C-08` 负责

---

## 5. 推荐实施路线

### 5.1 候选路线结论

本路线设计继续采用“**工程基线先行 + 主业务链优先 + 前端页面承载校验 + 横切能力分阶段归属**”的混合路线，但需要用 SQL 新口径重排后续阶段边界。

### 5.2 分阶段路线图

| 阶段 | 目标 | 关键交付 | 主要验证视角 |
|------|------|----------|--------------|
| 阶段 0 | 工程基线与真实 schema 对齐 | Boot 3.x 基线、配置骨架、统一响应异常、安全底座、SQL 脚本、基础枚举 | 仓库可启动、测试基座可运行、代码假设与 SQL 一致 |
| 阶段 1 | 认证、用户、角色与通知底座 | 登录注册、刷新 Token、密码重置、个人信息、用户状态、角色权限、站内信查询/已读、系统运维任务 | 三角色鉴权正确、通知页可基础联调 |
| 阶段 2 | 设备与分类主数据 | 分类树、设备 CRUD、图片上传、状态日志、设备状态流转 | 设备页、分类页、设备管理员链路可用 |
| 阶段 3 | 预约核心与审批模型 | 冲突检测、一审/二审、代预约、批量预约、预约批次、取消、审核通知 | 预约状态机与审批职责正确 |
| 阶段 4 | 签到与待人工处理 | 签到、待人工处理、预约提醒、借用确认超时任务 | 预约主链上半段闭环 |
| 阶段 5 | 借还与逾期治理 | 借用确认、归还确认、逾期识别、限制策略、解除、逾期通知、维修通知 | 预约到借还逾期全链闭环 |
| 阶段 6 | AI 与 Prompt 模板 | AI 对话、历史、Prompt 模板、降级策略 | AI 页面、模板页可联调 |
| 阶段 7 | 统计、联调与发布准备 | 预聚合统计、排名接口、日志、Docker、README、验收回归 | 三角色主要页面可验收、系统可发布 |

### 5.3 横切能力归属

- `RoleController` / 角色权限树前移到阶段 1，而不是晚期增强项
- 通知底座与已读接口前移到阶段 1，避免前端通知模块长期悬空
- `reservation_batch` 与代预约归入预约核心阶段，不得拖到后续增强阶段
- `PENDING_MANUAL` 虽由预约表承载，但其业务触发与借用确认强相关，应在阶段 4 建立入口，并在阶段 5 随借还链完成闭环
- 统计范围优先覆盖 SQL 已明确的聚合类型，不抢先扩展“个人统计 / 用户活跃度”等缺乏稳定真相源的能力

---

## 6. 文档与接口回写要求

以下事项不是业务待确认，而是**必须回写到文档**的同步项：

- 把通知接口族 `/api/notifications`、`/api/notifications/unread-count`、`/api/notifications/{id}/read`、`/api/notifications/read-all` 同步回写到 API 文档
- 把三角色与双审批模型回写到系统设计与术语文档
- 把 `reservation_batch`、`IN_APP`、`read_flag`、`read_at` 回写到后端目录稿和相关附录说明
- 把 Prompt 模板接口统一回写为 `/api/ai/prompts*`
- 把统计接口补齐为 `/api/statistics/overview`、`/api/statistics/device-utilization`、`/api/statistics/category-utilization`、`/api/statistics/borrow`、`/api/statistics/overdue`、`/api/statistics/hot-time-slots`、`/api/statistics/device-ranking`、`/api/statistics/user-ranking`
- 把 `statistics_daily` 的聚合来源明确写成 `reservation`、`borrow_record`、`overdue_record`、`device`、`device_category`、`user`，并说明查询层默认只读预聚合表

### 6.1 本轮 Task 14 回写结果

- 已补齐测试库中的 `statistics_daily` 与 `overdue_record`，用于支撑统计聚合与端到端 smoke 验证
- 已形成通知接口正式家族 `/api/notifications*` 与统计接口正式家族 `/api/statistics/*` 的实现与文档口径闭环
- 文档继续保持 SQL 新口径：三角色、`PENDING_DEVICE_APPROVAL` 等完整预约状态、`IN_APP` 已读能力、`statistics_daily` 聚合模型；不得回退到旧 `ADMIN` 或旧 `PENDING`

---

## 7. 非本期范围与唯一保留的未决项

### 7.1 明确不纳入本期

- `DialogConfig` / 对话配置管理
- 语音识别 / 语音合成
- RAG、联网检索、多模型编排、Agent 自主决策
- App / 小程序 / 微信通知 / 文件导出

### 7.2 仍需实施前确认的部署级问题

以下问题不影响本路线作为实施基线，但在真正开始编码前需要补一个部署级决定：

> [!question]+ 待实施前确认（部署级）
> - **冲突位置**: 管理员账号初始化策略
> - **冲突原因**: SQL 种子强调角色/权限初始化，但不内置默认管理员账号；本地联调和测试通常仍需管理员引导策略
> - **相关条目**: `device_management.sql`、计划文档、测试环境初始化脚本
> - **建议方案**: 生产库不预置管理员账号；`dev/test` 通过 profile 专用 seed 或 bootstrap runner 提供管理员测试账号

---

## 8. 对实施计划文档的要求

后续实施计划必须满足以下要求：

1. 所有任务都按 `com.jhun.backend` + `String UUID` + SQL 新口径编写
2. 文件地图中显式覆盖 `ReservationBatch`、通知已读接口、三角色、双审批、统计聚合字段
3. 若计划中引用 API 路径，需优先使用当前已确认路径：
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
4. 计划必须显式写清：哪些是“实现任务”，哪些是“文档回写任务”
5. `DialogConfig` 不进入本期计划

---

## 9. 结论

本路线设计选择：

- 保持当前仓库 `com.jhun.backend` 现状，不做无必要包根迁移
- 在工程基线阶段先把依赖、配置、安全和 SQL 口径对齐
- 后续所有业务阶段一律采用 SQL 新口径：三角色、双审批、`reservation_batch`、站内信已读、正式通知渠道和新的统计模型
- 对前端已存在承载的通知、角色权限、AI 历史和统计子页面，不再作为“以后再补”的边角模块处理

其核心目的，是让后续实施计划真正服务于可编码、可联调、可验收的后端落地，而不是继续在旧文档口径之间来回漂移。
