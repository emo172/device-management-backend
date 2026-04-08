# 智能设备管理系统后端

## 项目简介

本项目是“智能设备管理系统”后端，采用 Spring Boot + Spring Security + MyBatis-Plus 实现。

当前首轮已打通的主链路范围如下：

- 登录鉴权
- 设备与分类
- 预约 / 审批 / 签到
- 借还 / 逾期 / 通知
- AI 对话与语音辅助链路

当前仓库仍包含统计与 Prompt 模板等模块代码，但它们不属于本轮主链路综合验收范围。

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

### 3. 在 IDEA 中直接启动 Qwen 文本 AI，按需启用讯飞语音

- 仓库已提供共享运行配置：`.run/DeviceManagementBackend Dev Qwen.run.xml`
- 第一次导入项目后，在 IDEA 运行配置列表中选择 `DeviceManagementBackend Dev Qwen`
- 该运行配置会固定使用 `dev,local` profile；同时 `application-dev.yml` 已把文本 AI 默认切到 `enabled=true` 与 `provider=qwen`
- 本地私有覆盖统一放在 `config/application-local.yml`，该文件已被 `.gitignore` 忽略；新开发者可先复制 `config/application-local.example.yml`，再通过该私有文件或环境变量补齐自己的 MySQL、Qwen 与讯飞配置
- Qwen 密钥默认优先读取 `AI_QWEN_API_KEY`，同时兼容 `DASHSCOPE_API_KEY`
- 因此只要本地 MySQL、Redis 与 DashScope 访问链路可用，前端登录 `USER` 账号后进入 `/ai` 页面即可直接发起文本对话，不会再退回“仅支持查看历史会话”
- 若你临时需要回退为 mock 或关闭 AI，可在 IDEA 运行配置中覆盖环境变量：`AI_PROVIDER=mock` 或 `AI_ENABLED=false`

## AI 语音 v1 配置与发布前提

- `speech.enabled` / `/api/ai/capabilities` 中的 `speechEnabled` 只表示“语音输入转写可用”，默认应保持关闭；关闭时前端继续保留文字对话与历史查看
- 正式上传合同固定为 `audio/wav`（16k / 16bit / 单声道 PCM），单次录音时长上限固定为 60 秒；前后端后续实现都必须以此为准
- 后端语音实现已固定为讯飞 ASR-only；如需打开语音联调，只需要在后端注入 `SPEECH_IFLYTEK_APP_ID`、`SPEECH_IFLYTEK_API_KEY`、`SPEECH_IFLYTEK_API_SECRET`
- `/api/ai/speech/transcriptions` 只返回最终 transcript；前端收到结果后只回填输入框，仍需用户手动发送，不会自动触发聊天请求
- 一期只接入讯飞 `APPID / APIKey / APISecret`，不把热词 `res_id` 写成必配项，也不向前端暴露第三方凭据
- 发布阻塞浏览器矩阵仅覆盖桌面版 Chrome / Edge，Safari 与移动端暂不作为已正式支持范围
- 录音转写会经过第三方云语音服务处理，但原始录音不做持久化存储
- 后端不再提供历史播报或其他语音输出接口，聊天消息和历史页都只保留文本内容
- 语音入口关闭或浏览器不支持时，前端应继续保留文字对话与历史查看，不把回退路径当成异常

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
- 首轮综合验收仍不包含统计分析、Prompt 模板与批量预约结果页
- AI 语音 v1 仅以桌面版 Chrome / Edge 为发布阻塞浏览器范围，且上线前必须完成第三方云语音合规 / 隐私审批
- 通知中心当前采用轮询模型，未提供 SSE / WebSocket 实时推送
