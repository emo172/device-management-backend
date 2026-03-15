# 智能设备管理系统后端

## 项目简介

本项目是“智能设备管理系统”的 Spring Boot 后端实现，当前已覆盖认证与账户、用户与角色权限、设备与分类、预约、借还、通知、AI 对话基础能力，以及统计聚合与统计分析接口。

核心业务口径遵循仓库真相源：

- 固定三角色：`USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN`
- 预约审批模式：`DEVICE_ONLY` / `DEVICE_THEN_SYSTEM`
- 统一接口前缀：`/api/*`
- 统计接口统一读取 `statistics_daily` 预聚合表
- 通知接口统一使用 `/api/notifications*`，并采用 `IN_APP` / `EMAIL` / `SMS` 三渠道模型

## 通知能力说明

- `GET /api/notifications` 会回传通知页渲染和问题排查需要的关键字段：`status`、`readAt`、`templateVars`、`retryCount`、`relatedId`、`relatedType`，并补充 `sentAt`、`createdAt`
- `PUT /api/notifications/{id}/read` 与 `PUT /api/notifications/read-all` 只对 `IN_APP` 渠道生效，`EMAIL` / `SMS` 不参与已读状态流转
- 通知记录按 SQL 新口径保留模板变量、重试次数、关联业务信息，便于审批、逾期、代预约等链路复用

## 系统调度补充

- `C-09 TokenCleanupProcessor` 负责清理认证运行时中的过期验证码、刷新令牌快照和已失效登录锁定记录
- `C-10 SessionTimeoutProcessor` 负责清理认证运行时中的空闲会话快照，但不会把“存在会话快照”作为访问受保护接口的硬前置

## 环境要求

- Java 21
- Maven 3.9+
- MySQL 8.x
- Redis 7.x

## 本地启动

### 1. 准备数据库与 Redis

- 创建数据库：`device_management`
- 按需执行初始化脚本：`src/main/resources/sql/01_schema.sql`
- 启动本地 Redis

### 2. 启动开发环境

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

如需覆盖数据源、Redis 或 SMTP 配置，优先通过环境变量注入，不要直接把真实密钥写入仓库文件。

## 测试命令

```bash
# 运行全部测试
mvn clean test

# 只运行统计相关集成测试
mvn -Dtest=StatisticsControllerIntegrationTest,EndToEndSmokeIntegrationTest test

# 执行完整校验流程
mvn clean verify
```

## Docker 启动方式

### 1. 构建镜像

```bash
docker build -t device-management-backend:local .
```

### 2. 使用 Compose 启动最小联调骨架

```bash
docker compose up --build
```

Compose 默认拉起以下服务：

- `mysql`：MySQL 8.x
- `redis`：Redis 7.x
- `app`：后端应用容器

## 关键接口范围

当前仓库已对齐以下接口族：

- 认证与账户：`/api/auth/*`
- 用户管理：`/api/admin/users/*`
- 角色权限：`/api/admin/roles/*`
- 设备与分类：`/api/devices/*`、`/api/device-categories/*`
- 预约与批量预约：`/api/reservations/*`、`/api/reservation-batches/*`
- 借还管理：`/api/borrow-records/*`
- 通知管理：`/api/notifications`、`/api/notifications/unread-count`、`/api/notifications/{id}/read`、`/api/notifications/read-all`
- AI 对话与 Prompt：`/api/ai/*`、`/api/ai/prompts*`
- 统计分析：`/api/statistics/overview`、`/api/statistics/device-utilization`、`/api/statistics/category-utilization`、`/api/statistics/borrow`、`/api/statistics/overdue`、`/api/statistics/hot-time-slots`、`/api/statistics/device-ranking`、`/api/statistics/user-ranking`

## 统计聚合说明

- `StatisticsAggregationProcessor` 对齐 AGENTS 中的 `C-08` 任务，Cron 为 `0 30 2 * * ?`
- 聚合任务按日从 `reservation`、`borrow_record`、`overdue_record`、`device`、`device_category` 和 `user` 汇总数据
- 统计查询优先读取 `statistics_daily`，避免管理端接口直接扫描明细表

## 说明

- 测试环境使用 H2 内存数据库和 `src/test/resources/testdb/task3-schema.sql` / `task3-data.sql`
- 当前 Docker 与 Compose 文件提供“最小可运行骨架”，生产部署前仍需补充真实密钥、持久化、健康检查和监控配置
