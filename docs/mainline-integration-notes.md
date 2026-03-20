# 主链路联调说明

## 目标范围

本说明用于记录设备管理系统首轮主链路联调的真实启动顺序、验证口径与已知边界。

当前首轮纳入范围：

- 登录鉴权
- 设备与分类
- 预约 / 审批 / 签到
- 借还 / 逾期 / 通知

当前首轮不包含：

- AI
- 统计分析
- Prompt 模板
- 批量预约结果页

## 真相源

- 后端实际 Controller / DTO / Mapper XML
- 工作区根目录 `device_management.sql`
- 前端实际请求与页面承载

联调中如遇旧文档与实际实现冲突，优先以后端代码与 `device_management.sql` 为准。

## 启动顺序

### 1. 导入数据库脚本

```bash
mysql -u<user> -p < /mnt/d/WorkSpace/device_management.sql
```

说明：

- 脚本会创建 `device_management` 数据库并初始化三角色、权限、联调基础账号与密码历史
- 当前脚本已经内置 `smoke-user`、`smoke-device-admin`、`smoke-system-admin` 三个账号的角色基线，无需再额外提权

### 2. 准备 Redis

若你要按 `dev` 配置跑真实联调环境，建议同时准备本地 Redis；当前主链路自动化验证不把 Redis 视为唯一硬前置。

### 3. 启动后端

```bash
cd <当前后端仓库目录>
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. 启动前端

```bash
cd <当前前端仓库目录>
npm install
npm run dev
```

前端开发环境默认把 `/api` 与 `/files` 代理到 `http://localhost:8080`。

## 关键联调契约

- 统一响应壳：`{ code, message, data }`
- 统一成功判定：`code === 0`
- 主键统一为 `string` UUID
- 时间统一为 ISO：`yyyy-MM-ddTHH:mm:ss`
- 设备图片统一走 `/files/devices/**`
- 前端主展示与写接口标准口径：
  - 审批模式：`DEVICE_ONLY` / `DEVICE_THEN_SYSTEM`
  - 预约模式：`SELF` / `ON_BEHALF`
  - 签到状态：`NOT_CHECKED_IN` / `CHECKED_IN` / `CHECKED_IN_TIMEOUT`
- 借还 / 逾期 / 通知页面遵循“名称优先、缺失回退 ID”，不伪造后端未返回字段

## smoke 账号与数据准备

### 账号准备

`device_management.sql` 已经写入以下账号：

- `smoke-user`
- `smoke-device-admin`
- `smoke-system-admin`

注意：

- SQL 中只保存 BCrypt 哈希，不回写明文密码
- 当前三类种子账号已核对可使用统一默认密码：`Password123!`
- 如果你明确知道这组三个账号对应的明文密码，可直接用于人工冒烟
- 如果你想改成本地自定义密码，优先直接更新现有三条种子账号的密码哈希，避免误删管理员角色
- 如果你要重新注册一组新账号，需注意注册接口默认只会创建 `USER`，因此后续还要在 MySQL 中显式提权

示例 SQL：

```sql
UPDATE user u
JOIN role r ON r.name = 'DEVICE_ADMIN'
SET u.role_id = r.id
WHERE u.username = 'your-device-admin';

UPDATE user u
JOIN role r ON r.name = 'SYSTEM_ADMIN'
SET u.role_id = r.id
WHERE u.username = 'your-system-admin';
```

### 业务数据准备

使用设备管理员账号准备两组联调数据：

- 分类 A：`Smoke Device Only`，默认审批模式 `DEVICE_ONLY`
- 分类 B：`Smoke Device Then System`，默认审批模式 `DEVICE_THEN_SYSTEM`
- 设备 A：`SMOKE-DEVICE-ONLY-01`
- 设备 B：`SMOKE-DEVICE-THEN-SYSTEM-01`

两台设备都应保持 `AVAILABLE`。

## 自动验证命令

### 后端

```bash
cd <当前后端仓库目录>
./mvnw clean verify
```

### 前端

```bash
cd <当前前端仓库目录>
npm run type-check && npm run build && npm run test:unit
```

## 最新自动验证记录

### 2026-03-20

- 后端：`./mvnw clean verify` 通过，统一汇总为 `Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`
- 前端：`npm run type-check && npm run build && npm run test:unit` 通过，`95` 个测试文件、`337` 个测试全部通过
- 前端构建存在 Vite chunk 体积告警，但本次不影响构建成功与主链路验收结论

## 人工冒烟顺序

### 路径 A：`DEVICE_ONLY`

1. `smoke-user` 创建预约
2. `smoke-device-admin` 完成一审
3. `smoke-user` 进入签到页并签到
4. `smoke-device-admin` 确认借用
5. `smoke-device-admin` 确认归还
6. `smoke-user` 打开通知中心核对站内信与未读数变化

### 路径 B：`DEVICE_THEN_SYSTEM`

1. `smoke-user` 创建预约
2. `smoke-device-admin` 完成一审
3. `smoke-system-admin` 完成二审
4. `smoke-user` 进入签到页并签到
5. `smoke-device-admin` 确认借用
6. `smoke-device-admin` 确认归还
7. `smoke-user` 打开通知中心核对站内信与未读数变化

## 已知边界

- 本轮不处理 AI、统计、Prompt 模板和批量预约结果页
- 通知中心当前采用轮询刷新，不提供 WebSocket / SSE 实时推送
- 若本地环境未准备 MySQL / Redis，或无法确认 smoke 账号明文密码，则只能先完成自动化验证，人工冒烟需要待环境补齐后再执行
