# 主链路联调说明

## 目标范围

本说明用于记录设备管理系统首轮主链路联调的真实启动顺序、验证口径与已知边界。

当前首轮纳入范围：

- 登录鉴权
- 设备与分类
- 预约 / 审批 / 签到
- 借还 / 逾期 / 通知
- AI 对话与语音辅助链路

当前首轮不包含：

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

## AI 语音 v1 联调前提

- 后端 `speech.enabled` 是语音总开关，保持 `false` 时前端必须优雅回退到文字对话与历史查看
- 建议先复制 `config/application-local.example.yml` 到本地私有 `config/application-local.yml`，或等价地通过环境变量准备讯飞 `APPID / APIKey / APISecret`
- 后端语音实现已固定为讯飞 ASR-only，联调前至少准备 `SPEECH_IFLYTEK_APP_ID`、`SPEECH_IFLYTEK_API_KEY`、`SPEECH_IFLYTEK_API_SECRET`
- 当前发布阻塞浏览器矩阵仅覆盖桌面版 Chrome / Edge，不把 Safari 或移动端写成已正式支持
- 浏览器录音正式上传口径固定为 `audio/wav`（16k / 16bit / 单声道 PCM）；拿不到该合同能力时前端必须直接回退文字链路
- 单次录音时长上限固定为 60 秒；一期只做输入转写，不接入热词 `res_id`
- `/api/ai/speech/transcriptions` 只返回最终 transcript，前端收到后只回填输入框，仍需用户手动发送
- 录音转写会经过第三方云语音服务处理，但原始录音不做持久化存储
- 后端不再提供历史播放或其他语音输出链路，聊天历史只保留文本内容
- 第三方云语音的合规 / 隐私审批是上线前置条件，当前文档同步不代表审批已经完成

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

reservation-create 的文档主顺序仍固定为“后端测试 → 前端 type-check/build/unit → 前端 seed 准备 → 浏览器 E2E”。
其中 reservation-create seed 脚本会直接命中 `POST /api/internal/seeds/reservation-create`；该入口现在要求“共享令牌 + loopback”双重保护，因此脚本必须显式携带 `X-Internal-Seed-Token`，并在本机调用后端。

### 第 1 步：后端测试

```bash
cd <当前后端仓库目录>
./mvnw clean verify
```

### 第 2 步：前端静态验证与单元测试

```bash
cd <当前前端仓库目录>
npm run type-check && npm run build && npm run test:unit
```

### 第 3 步：前端 seed 准备（reservation-create）

```bash
cd <当前前端仓库目录>

INTERNAL_RESERVATION_CREATE_SEED_TOKEN=<token> RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 node scripts/e2e/seed-reservation-create.mjs --scenario happy-path
INTERNAL_RESERVATION_CREATE_SEED_TOKEN=<token> RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 node scripts/e2e/seed-reservation-create.mjs --scenario atomic-failure

INTERNAL_RESERVATION_CREATE_SEED_TOKEN=<token> PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/google-chrome \
RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 npx playwright test --config=playwright.reservation.config.mjs
```

补充说明：

- `seed-reservation-create.mjs` 会把真实账号、设备、时间窗和冲突信息写入 `.tmp/reservation-create-seed/<scenario>.json`，供后续浏览器脚本或人工联调复用。
- 后端若以 `test` profile 本地启动，需要同时提供 `INTERNAL_SEED_RESERVATION_CREATE_ENABLED=true` 与 `INTERNAL_RESERVATION_CREATE_SEED_TOKEN=<token>`。
- Node 侧 seed 脚本要求绝对 backend URL；若当前前端环境把 `VITE_API_BASE_URL` 配成相对路径，请额外设置 `RESERVATION_CREATE_BACKEND_URL=http://localhost:8080`。
- `playwright.reservation.config.mjs` 默认把前端源站固定在 `http://localhost:5173`，以匹配当前后端 CORS 白名单；若联调时改端口，必须同步评估 CORS 放行列表。
- `e2e/reservation-create.spec.ts` 会在每条用例开始前刷新对应 scenario 的种子，避免 `atomic-failure` 的冲突样本反向污染 `happy-path`。

## 最新自动验证记录

### 2026-04-07

- 后端：`./mvnw clean verify` 通过。
- 前端：`npm run type-check && npm run build && npm run test:unit` 通过。
- 前端：`npm run type-check && npm run build` 通过。

### 2026-04-08

- 后端：真实 MySQL 旧库升级演练通过；应用可启动、`reservation_device` 自动补齐、`reservation.device_id` 放宽为可空，且旧预约样本成功回填到 `reservation_device`。
- 前端：`npm run type-check` 通过。
- 前端：`npm run build` 通过。
- 前端：`npm run test:unit` 通过，汇总为 `106 files / 682 tests passed`。
- 前端：`INTERNAL_RESERVATION_CREATE_SEED_TOKEN=test-seed-token RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 node scripts/e2e/seed-reservation-create.mjs --scenario happy-path` 通过。
- 前端：`INTERNAL_RESERVATION_CREATE_SEED_TOKEN=test-seed-token RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 node scripts/e2e/seed-reservation-create.mjs --scenario atomic-failure` 通过。
- 前端：`INTERNAL_RESERVATION_CREATE_SEED_TOKEN=test-seed-token PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/google-chrome RESERVATION_CREATE_BACKEND_URL=http://127.0.0.1:18083 npx playwright test --config=playwright.reservation.config.mjs` 通过，汇总为 `2 passed`。

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

- 本轮仍不处理统计、Prompt 模板和批量预约结果页
- AI 语音 v1 仅以桌面版 Chrome / Edge 为发布阻塞浏览器范围，且上线前必须完成第三方云语音合规 / 隐私审批
- 通知列表兼容接口保持 `GET /api/notifications` 数组返回；服务端分页与通知类型筛选通过 `GET /api/notifications/page` 承接
- 通知中心当前采用轮询刷新，不提供 WebSocket / SSE 实时推送
- 若本地环境未准备 MySQL / Redis，或无法确认 smoke 账号明文密码，则只能先完成自动化验证，人工冒烟需要待环境补齐后再执行
- reservation-create internal seed 默认要求本机 loopback 访问；若联调链路经过 Vite/Nginx 代理、Docker bridge 或本机局域网 IP，需要在受控环境下显式评估是否关闭 `internal.seed.reservation-create.loopback-only`
