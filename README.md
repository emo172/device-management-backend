# 智能设备管理系统后端

## 项目简介

本项目是“智能设备管理系统”后端，采用 Spring Boot + Spring Security + MyBatis-Plus 实现。

当前首轮已打通的主链路范围如下：

- 登录鉴权
- 设备与分类
- 预约 / 审批 / 签到
- 借还 / 逾期 / 通知

当前仓库仍包含 AI、统计与 Prompt 模板等模块代码，但它们不属于本轮主链路综合验收范围。

## 联调真相源

- 工作区根目录 `device_management.sql`
- 当前仓库实际 Controller / DTO / Mapper XML / 配置
- 前端仓库实际请求与页面承载

核心契约如下：

- 固定三角色：`USER` / `DEVICE_ADMIN` / `SYSTEM_ADMIN`
- 统一接口前缀：`/api/*`
- 统一响应壳：`{ code, message, data }`
- 主键统一为 `String` UUID
- 时间统一为 ISO：`yyyy-MM-ddTHH:mm:ss`
- 设备图片统一通过 `/files/devices/**` 对外访问

## 环境要求

- Java 21
- Maven 3.9+
- MySQL 8.x
- Redis 7.x

## 本地启动

### 1. 导入数据库脚本

```bash
cd <当前后端仓库目录>
mysql -u<user> -p < /mnt/d/WorkSpace/device_management.sql
```

说明：

- 当前联调真相源已经从仓库内旧初始化脚本切换到工作区根目录 `device_management.sql`
- 该脚本会初始化三角色、权限、联调 smoke 账号以及密码历史

### 2. 启动开发环境

```bash
cd <当前后端仓库目录>
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

如需覆盖数据源、Redis 或 SMTP 配置，优先使用环境变量或本地私有配置，不要把真实密钥写回仓库。

## smoke 联调说明

- `device_management.sql` 已内置 `smoke-user`、`smoke-device-admin`、`smoke-system-admin` 三个账号的角色基线
- 当前三类种子账号已核对可使用统一默认密码：`Password123!`
- 若你已掌握这组账号的明文密码，可直接用于人工冒烟
- 若你希望改成自定义密码，优先直接更新这三条种子账号的密码哈希，避免把管理员角色一并删掉
- 若你要重新注册一组新账号，需注意注册接口默认只创建 `USER`；后续必须在 MySQL 中把对应账号显式更新为 `DEVICE_ADMIN` / `SYSTEM_ADMIN`
- 更完整的主链路联调顺序见 `docs/mainline-integration-notes.md`

## 测试与验证命令

```bash
# 执行完整后端校验
cd <当前后端仓库目录>
./mvnw clean verify

# 只启动本地开发环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## 关键接口范围

当前仓库已对齐以下接口族：

- 认证与账户：`/api/auth/*`
- 用户管理：`/api/admin/users/*`
- 角色权限：`/api/admin/roles/*`
- 设备与分类：`/api/devices/*`、`/api/device-categories/*`
- 预约与批量预约：`/api/reservations/*`、`/api/reservation-batches/*`
- 借还管理：`/api/borrow-records/*`
- 逾期管理：`/api/overdue-records/*`
- 通知管理：`/api/notifications`（兼容当前通知数组列表）、`/api/notifications/page`（分页筛选扩展）及其余 `/api/notifications/*`
- AI 对话与 Prompt：`/api/ai/*`、`/api/ai/prompts*`
- 统计分析：`/api/statistics/*`

## 当前已知边界

- 主链路联调已覆盖 `/files/devices/**` 图片出口、登录鉴权、设备分类、预约审批、借还逾期、通知已读模型
- 首轮综合验收不包含 AI、统计分析、Prompt 模板与批量预约结果页
- 通知中心当前采用轮询模型，未提供 SSE / WebSocket 实时推送
