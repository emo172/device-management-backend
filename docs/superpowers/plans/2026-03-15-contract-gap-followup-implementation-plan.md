# Contract Gap Follow-up Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐预约、用户、角色权限树和 Prompt 删除等后端契约缺口，并同步收口通知字段、系统定时清理逻辑、并发测试与文档口径。

**Architecture:** 保持现有 `Controller -> Service -> Mapper` 三层结构，在当前分支已存在首轮实现的基础上，按“先补失败测试/验证，再做最小修正”的方式收口契约。预约取消、权限树读取和 Prompt 删除都通过显式接口承载业务规则，通知字段与定时任务则作为统一收口任务同步补齐测试与文档，避免只改代码不改契约。

**Tech Stack:** Java 21, Spring Boot, Spring Security, MyBatis-Plus, MyBatis XML, JUnit 5, MockMvc, Maven

---

## Chunk 1: 预约与用户查询契约补齐

### Task 1: 预约列表、详情与取消

**Files:**
- Modify: `src/main/java/com/jhun/backend/controller/ReservationController.java`
- Modify: `src/main/java/com/jhun/backend/service/ReservationService.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/ReservationServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/mapper/ReservationMapper.java`
- Modify: `src/main/resources/mapper/ReservationMapper.xml`
- Modify: `src/test/java/com/jhun/backend/integration/reservation/ReservationControllerIntegrationTest.java`
- Modify: `src/main/java/com/jhun/backend/dto/reservation/ReservationListItemResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/reservation/ReservationPageResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/reservation/ReservationDetailResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/reservation/CancelReservationRequest.java`

- [ ] **Step 1: 先补/校正预约列表测试，再锁定当前缺口**

```java
mockMvc.perform(get("/api/reservations")
        .header("Authorization", bearer(user, "USER")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.records[0].id").exists());
```

- [ ] **Step 2: 运行预约列表测试并确认失败或暴露字段/权限不一致**

Run: `mvn -Dtest=ReservationControllerIntegrationTest#shouldListOnlyOwnReservationsForUser test`
Expected: FAIL，提示分页字段、权限口径或返回字段与预期不一致

- [ ] **Step 3: 先补/校正预约详情测试，再锁定详情字段和访问边界**

```java
mockMvc.perform(get("/api/reservations/{id}", reservationId)
        .header("Authorization", bearer(user, "USER")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.startTime").value("2026-03-20T09:00:00"));
```

- [ ] **Step 4: 运行预约详情测试并确认失败或暴露字段/权限不一致**

Run: `mvn -Dtest=ReservationControllerIntegrationTest#shouldAllowUserToGetOwnReservationDetail test`
Expected: FAIL，提示详情字段、访问边界或返回值不符合契约

- [ ] **Step 5: 先补/校正预约取消测试，再锁定取消窗口规则**

```java
mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
        .header("Authorization", bearer(user, "USER"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{"reason":"课程取消"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
```

- [ ] **Step 6: 运行预约取消测试并确认失败或暴露取消规则缺口**

Run: `mvn -Dtest=ReservationControllerIntegrationTest#shouldAllowUserCancelReservationBeforeTwentyFourHours test`
Expected: FAIL，提示取消窗口、返回字段或角色边界未完全满足契约

- [ ] **Step 7: 按失败结果修正预约查询与取消实现**

实现要点：

- `GET /api/reservations` 支持当前用户视角查询，普通用户只能看自己；管理员可按角色边界扩展查询
- `GET /api/reservations/{id}` 返回时间、用途、备注、审批快照、签到、取消信息等详情字段
- `POST /api/reservations/{id}/cancel` 强校验“开始前超过 24 小时用户可自行取消；开始前 24 小时内拒绝自助取消；开始后不可取消”
- 取消成功后写入 `status=CANCELLED`、`cancelReason`、`cancelTime`
- DTO、Mapper XML 与中文注释同步补齐

- [ ] **Step 8: 运行预约控制器相关测试并确认通过**

Run: `mvn -Dtest=ReservationControllerIntegrationTest,ReservationConflictConcurrencyIT test`
Expected: PASS

### Task 2: 用户列表与详情

**Files:**
- Modify: `src/main/java/com/jhun/backend/controller/UserController.java`
- Modify: `src/main/java/com/jhun/backend/service/UserService.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/mapper/UserMapper.java`
- Modify: `src/main/resources/mapper/UserMapper.xml`
- Modify: `src/test/java/com/jhun/backend/integration/user/UserControllerIntegrationTest.java`
- Modify: `src/main/java/com/jhun/backend/dto/user/UserListItemResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/user/UserPageResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/user/UserDetailResponse.java`

- [ ] **Step 1: 先补/校正用户列表与详情测试**
- [ ] **Step 2: 运行 `mvn -Dtest=UserControllerIntegrationTest#shouldAllowSystemAdminToListUsers,UserControllerIntegrationTest#shouldAllowSystemAdminToGetUserDetail test` 并确认失败或暴露字段缺口**
- [ ] **Step 3: 按失败结果修正 `GET /api/admin/users` 与 `GET /api/admin/users/{id}`**

实现要点：

- 保持 `SYSTEM_ADMIN` 专属能力，不开放给 `DEVICE_ADMIN` / `USER`
- 列表至少返回 `userId`、`username`、`email`、`status`、`freezeStatus`、`roleId`、`roleName`
- 详情补充真实姓名、手机号、最后登录时间、创建时间等后台必要信息
- MyBatis XML 需显式说明列表筛选和详情关联角色名称的用途

- [ ] **Step 6: 运行用户控制器测试并确认通过**

Run: `mvn -Dtest=UserControllerIntegrationTest test`
Expected: PASS

## Chunk 2: 角色权限树与 Prompt 删除

### Task 3: 动态权限树读取

**Files:**
- Modify: `src/main/java/com/jhun/backend/controller/RoleController.java`
- Modify: `src/main/java/com/jhun/backend/service/RoleService.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/RoleServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/mapper/RoleMapper.java`
- Modify: `src/main/resources/mapper/RoleMapper.xml`
- Modify: `src/test/java/com/jhun/backend/integration/role/RoleControllerIntegrationTest.java`
- Modify: `src/main/java/com/jhun/backend/dto/role/RolePermissionTreeRow.java`
- Modify: `src/main/java/com/jhun/backend/dto/role/RolePermissionTreeNodeResponse.java`
- Modify: `src/main/java/com/jhun/backend/dto/role/RolePermissionTreeResponse.java`

- [ ] **Step 1: 先补/校正权限树读取测试**
- [ ] **Step 2: 运行 `mvn -Dtest=RoleControllerIntegrationTest#shouldAllowSystemAdminToReadRolePermissionTree test` 并确认失败或暴露树结构/勾选态缺口**
- [ ] **Step 3: 按失败结果修正 `GET /api/admin/roles/{id}/permissions/tree`**

实现要点：

- 返回按模块分组的权限树节点和已选权限勾选态，角色基础信息继续由角色列表接口承载
- 树结构按父子关系或模块分组稳定排序，避免前端自行拼树
- 沿用三角色模型，不引入旧 `ADMIN` 口径

- [ ] **Step 4: 运行角色控制器测试并确认通过**

Run: `mvn -Dtest=RoleControllerIntegrationTest test`
Expected: PASS

### Task 4: Prompt 删除

**Files:**
- Modify: `src/main/java/com/jhun/backend/controller/PromptTemplateController.java`
- Modify: `src/main/java/com/jhun/backend/service/PromptTemplateService.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/PromptTemplateServiceImpl.java`
- Modify: `src/test/java/com/jhun/backend/integration/ai/PromptTemplateControllerIntegrationTest.java`

- [ ] **Step 1: 先补/校正 Prompt 删除测试**
- [ ] **Step 2: 运行 `mvn -Dtest=PromptTemplateControllerIntegrationTest#shouldAllowSystemAdminToDeleteInactivePromptTemplate test` 并确认失败或暴露删除规则缺口**
- [ ] **Step 3: 按失败结果修正 `DELETE /api/ai/prompts/{id}` 的删除能力**

实现要点：

- 仅 `SYSTEM_ADMIN` 可删除
- 默认保护启用中的模板不可直接删除，先停用再删
- 删除不存在模板时返回明确业务异常

- [ ] **Step 4: 运行 Prompt 模板测试并确认通过**

Run: `mvn -Dtest=PromptTemplateControllerIntegrationTest test`
Expected: PASS

## Chunk 3: 通知、调度器、并发测试与文档收口

### Task 5: 通知返回字段与接口验证

**Files:**
- Modify: `src/main/java/com/jhun/backend/dto/notification/NotificationResponse.java`
- Modify: `src/main/java/com/jhun/backend/service/impl/NotificationServiceImpl.java`
- Modify: `src/test/java/com/jhun/backend/integration/notification/NotificationControllerIntegrationTest.java`
- Modify: `README.md`

- [ ] **Step 1: 先补/校正通知列表字段测试**
- [ ] **Step 2: 运行 `mvn -Dtest=NotificationControllerIntegrationTest#shouldReturnNotificationListWithContractFields test` 并确认失败或暴露字段缺口**
- [ ] **Step 3: 扩充通知响应字段并补齐非 IN_APP 已读保护测试**
- [ ] **Step 4: 运行通知控制器测试并确认通过**

Run: `mvn -Dtest=NotificationControllerIntegrationTest test`
Expected: PASS

### Task 6: C-09 / C-10 实逻辑与认证内存态清理支撑

**Files:**
- Modify: `src/main/java/com/jhun/backend/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/jhun/backend/scheduler/system/TokenCleanupProcessor.java`
- Modify: `src/main/java/com/jhun/backend/scheduler/system/SessionTimeoutProcessor.java`
- Modify: `src/main/java/com/jhun/backend/service/support/auth/AuthRuntimeStateSupport.java`
- Modify: `src/test/java/com/jhun/backend/unit/service/support/auth/AuthRuntimeStateSupportTest.java`
- Modify: `src/test/java/com/jhun/backend/unit/scheduler/system/TokenCleanupProcessorTest.java`
- Modify: `src/test/java/com/jhun/backend/unit/scheduler/system/SessionTimeoutProcessorTest.java`

- [ ] **Step 1: 写失败测试，锁定认证内存态清理与调度器行为**
- [ ] **Step 2: 运行相关单测并确认失败**
- [ ] **Step 3: 抽离登录失败状态/验证码状态存储并实现真实清理逻辑**

实现要点：

- 不伪造 Redis 持久化，先对当前内存态状态容器提供可观测、可清理的 support 组件
- `C-09` 负责清理过期验证码、失效刷新令牌占位数据和已失效登录失败锁定记录
- `C-10` 负责清理超时会话态占位数据
- 调度器与 support 组件都要有中文注释说明“当前为何采用内存态、边界在哪里”

- [ ] **Step 4: 运行调度器与认证支撑单测并确认通过**

Run: `mvn -Dtest=AuthServiceTest,TokenCleanupProcessorTest,SessionTimeoutProcessorTest test`
Expected: PASS

### Task 7: 50 并发回归与文档口径同步

**Files:**
- Modify: `src/test/java/com/jhun/backend/integration/reservation/ReservationConflictConcurrencyIT.java`
- Modify: `docs/superpowers/plans/2026-03-11-device-management-backend-implementation-plan.md`
- Modify: `docs/superpowers/specs/2026-03-11-device-management-implementation-roadmap-design.md`
- Modify: `README.md`

- [ ] **Step 1: 将预约并发测试提升到计划要求的 50 并发，并先运行确认当前不满足或未覆盖到位**
- [ ] **Step 2: 修正计划文档和设计文档中的契约口径，补充本次新增接口**
- [ ] **Step 3: 运行本次收口相关测试集**

Run: `mvn -Dtest=ReservationControllerIntegrationTest,ReservationConflictConcurrencyIT,UserControllerIntegrationTest,RoleControllerIntegrationTest,PromptTemplateControllerIntegrationTest,NotificationControllerIntegrationTest,AuthServiceTest test`
Expected: PASS

- [ ] **Step 4: 运行阶段性完整验证**

Run: `mvn clean test`
Expected: BUILD SUCCESS
