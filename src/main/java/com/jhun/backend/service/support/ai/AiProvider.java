package com.jhun.backend.service.support.ai;

import com.jhun.backend.config.ai.AiProperties;

/**
 * AI Provider 抽象。
 * <p>
 * 该接口把服务层对 AI 能力的依赖从具体实现类收敛为统一契约，确保 `AiServiceImpl` 只关心：
 * 1) 当前运行时选中了哪个 provider；
 * 2) provider 能否基于“当前用户上下文 + 当前这一轮输入”产出统一结果对象。
 * 当前阶段先承接 `mock` 规则降级链路，后续 Qwen provider 也必须复用同一份抽象，避免服务层再次回到直连具体类的耦合状态。
 * <p>
 * Task 6 引入真实工具调用后，provider 必须知道当前登录用户与角色，
 * 才能在不越过 `AiToolExecutionService` 边界的前提下安全执行查询、预约和取消工具。
 */
public interface AiProvider {

    AiProperties.Provider provider();

    AiProviderResult process(String userId, String role, String message);
}
