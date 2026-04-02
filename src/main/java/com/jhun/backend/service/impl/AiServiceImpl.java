package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.dto.ai.AiHistoryDetailResponse;
import com.jhun.backend.dto.ai.AiHistorySummaryResponse;
import com.jhun.backend.entity.ChatHistory;
import com.jhun.backend.mapper.ChatHistoryMapper;
import com.jhun.backend.service.AiService;
import com.jhun.backend.service.support.ai.AiProvider;
import com.jhun.backend.service.support.ai.AiProviderResult;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 对话服务实现。
 * <p>
 * 当前阶段的 AI 主链路已经同时支持 `mock` 与 `qwen` 两类 provider，但服务层不再直连任何具体实现，
 * 而是通过 `AiProvider` 抽象 + `AiRuntimeProperties` 统一收口 AI 开关、Provider 选择和历史落库语义；
 * 所有业务动作仍然必须经过 provider 内部的正式工具执行层与既有 Service 边界，
 * 服务层自身只负责角色守卫、Provider 调度和历史留痕，不直接复制工具执行或 Qwen 编排逻辑。
 */
@Service
public class AiServiceImpl implements AiService {

    /**
     * AI 历史列表固定上限。
     * <p>
     * 当前任务只要求避免一次性返回全部历史，因此采用最小改动策略在服务层收敛到固定条数，
     * 暂不引入完整分页模型，后续若前端需要翻页可再扩展正式分页参数。
     */
    private static final int HISTORY_LIST_LIMIT = 20;

    private final ChatHistoryMapper chatHistoryMapper;
    private final Map<AiProperties.Provider, AiProvider> aiProviders;
    private final AiRuntimeProperties aiRuntimeProperties;

    public AiServiceImpl(
            ChatHistoryMapper chatHistoryMapper,
            List<AiProvider> aiProviders,
            AiRuntimeProperties aiRuntimeProperties) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.aiProviders = indexProviders(aiProviders);
        this.aiRuntimeProperties = aiRuntimeProperties;
    }

    /**
     * 处理一轮 AI 文本对话并写入历史记录。
     * <p>
     * 该方法首先校验当前角色必须为 `USER`，避免设备管理员或系统管理员从 AI 入口越过正式业务边界；
     * 随后调用当前激活的 provider 产出统一结果，并把本轮输入、回复、意图、执行状态、结构化提取结果、唯一资源主键和耗时落到 `chat_history`，
     * 作为历史页和后续 AI 能力排查的唯一过程留痕。当前阶段副作用仅限写入 AI 历史表，不会直接修改预约、设备等业务表。
     * <p>
     * 这里故意不再把整轮 chat 包在一个共享事务里：qwen provider 内部的工具执行会进入正式业务服务事务，
     * 一旦业务拒绝在下游把共享事务标记为 rollback-only，即使 AI 层已经把结果收口成 `FAILED` 并继续写历史，
     * 外层提交时仍会抛出 `UnexpectedRollbackException`，反而让“业务写入回滚 + FAILED 历史留痕”这两个目标无法同时成立。
     * 因此当前方法只让正式业务动作沿用各自服务事务边界，`chat_history` 则在 provider 返回后单独写入，
     * 从而保持：成功/失败业务写入仍按原规则提交或回滚，AI 历史也能稳定保留最终受控结果。
     *
     * @param userId 当前登录用户 ID，用于绑定历史归属
     * @param role 当前登录角色，用于执行 AI 使用边界校验
     * @param request 对话请求，包含可选会话 ID 与用户输入文本
     * @return 本轮对话的响应结果，包含历史 ID、会话 ID、意图、执行状态和 AI 回复
     */
    @Override
    public AiChatResponse chat(String userId, String role, AiChatRequest request) {
        validateAiRole(role);
        validateChatEnabled();
        long startedAt = System.currentTimeMillis();
        AiProviderResult result = resolveActiveProvider().process(userId, role, request.message());

        ChatHistory history = new ChatHistory();
        history.setId(UuidUtil.randomUuid());
        history.setUserId(userId);
        history.setSessionId(resolveSessionId(request.sessionId()));
        history.setUserInput(request.message().trim());
        history.setAiResponse(result.aiResponse());
        history.setIntent(result.intent());
        history.setIntentConfidence(result.intentConfidence());
        applyProviderResult(history, result);
        history.setResponseTimeMs((int) (System.currentTimeMillis() - startedAt));
        history.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(history);

        return new AiChatResponse(
                history.getId(),
                history.getSessionId(),
                history.getIntent(),
                history.getExecuteResult(),
                history.getAiResponse());
    }

    /**
     * 查询当前用户的 AI 历史列表。
     * <p>
     * 列表查询同样要求调用方角色为 `USER`，并且只按当前登录用户 ID 检索历史，
     * 从实现层确保“本人历史隔离”规则成立，避免跨用户查看对话内容。
     *
     * @param userId 当前登录用户 ID
     * @param role 当前登录角色，用于执行 AI 使用边界校验
     * @return 当前用户可见的 AI 历史摘要列表，不产生写入副作用
     */
    @Override
    public List<AiHistorySummaryResponse> listHistory(String userId, String role) {
        validateAiRole(role);
        return chatHistoryMapper.findByUserId(userId).stream()
                .limit(HISTORY_LIST_LIMIT)
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * 查询当前用户的一条 AI 历史详情。
     * <p>
     * 详情查询除了要求调用方是 `USER` 外，还必须同时满足“记录属于当前用户”这一隔离条件；
     * 若以当前用户 ID 和历史 ID 组合查询不到记录，则视为不存在并抛出业务异常，避免把越权访问暴露为可观察到的他人数据。
     *
     * @param userId 当前登录用户 ID，用于本人历史隔离校验
     * @param role 当前登录角色，用于执行 AI 使用边界校验
     * @param historyId 待查询的历史记录 ID
     * @return 当前用户拥有的 AI 历史详情，不产生写入副作用
     */
    @Override
    public AiHistoryDetailResponse getHistoryDetail(String userId, String role, String historyId) {
        validateAiRole(role);
        ChatHistory history = chatHistoryMapper.findOwnedById(userId, historyId);
        if (history == null) {
            throw new BusinessException("AI 对话历史不存在");
        }
        return toDetailResponse(history);
    }

    /**
     * 校验 AI 使用角色边界。
     * <p>
     * 根据任务范围，只有普通用户可以使用 AI 文本对话与查询本人历史；
     * 设备管理员和系统管理员都不得从该入口触发 AI 逻辑，否则会混淆业务使用入口与后台管理入口的边界。
     *
     * @param role 当前登录角色编码
     */
    private void validateAiRole(String role) {
        if (!"USER".equals(role)) {
            throw new BusinessException("只有普通用户可以使用 AI 对话");
        }
    }

    /**
     * 校验新的 AI 对话入口是否已被开启。
     * <p>
     * `ai.enabled=false` 只阻断新的 `/api/ai/chat` 请求，不影响历史列表、历史详情和 Prompt 管理等其他 AI 相关接口；
     * 因此该校验只放在聊天入口方法中，而不是抽到所有 AI 服务方法的公共前置逻辑里。
     */
    private void validateChatEnabled() {
        if (!aiRuntimeProperties.isChatEnabled()) {
            throw new BusinessException("AI 对话功能当前已关闭，请联系管理员开启 ai.enabled 后再试");
        }
    }

    /**
     * 解析本轮对话的会话 ID。
     * <p>
     * 若前端已传入会话 ID，则继续沿用并做首尾空白清理，用于把多轮对话归并到同一会话；
     * 若未传或为空白，则生成新的 UUID，避免历史记录失去会话维度。
     *
     * @param sessionId 前端传入的会话 ID，可为空
     * @return 可直接写入历史表的有效会话 ID
     */
    private String resolveSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? UuidUtil.randomUuid() : sessionId.trim();
    }

    private Map<AiProperties.Provider, AiProvider> indexProviders(List<AiProvider> providers) {
        Map<AiProperties.Provider, AiProvider> indexedProviders = new EnumMap<>(AiProperties.Provider.class);
        for (AiProvider provider : providers) {
            AiProvider previousProvider = indexedProviders.putIfAbsent(provider.provider(), provider);
            if (previousProvider != null) {
                throw new IllegalStateException("AI Provider 类型 [%s] 存在多个实现，无法确定唯一运行时入口"
                        .formatted(provider.provider().name().toLowerCase()));
            }
        }
        return Map.copyOf(indexedProviders);
    }

    private AiProvider resolveActiveProvider() {
        AiProvider aiProvider = aiProviders.get(aiRuntimeProperties.provider());
        if (aiProvider == null) {
            throw new IllegalStateException("当前未找到 provider=%s 对应的 AI Provider 实现"
                    .formatted(aiRuntimeProperties.provider().name().toLowerCase()));
        }
        return aiProvider;
    }

    private void applyProviderResult(ChatHistory history, AiProviderResult result) {
        history.setExtractedInfo(normalizeOptionalText(result.extractedInfo()));
        history.setDeviceId(normalizeOptionalText(result.deviceId()));
        history.setReservationId(normalizeOptionalText(result.reservationId()));
        history.setExecuteResult(result.executeResult());
        history.setErrorMessage(normalizeOptionalText(result.errorMessage()));
        history.setLlmModel(normalizeOptionalText(result.providerName()));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AiHistorySummaryResponse toSummaryResponse(ChatHistory history) {
        return new AiHistorySummaryResponse(
                history.getId(),
                history.getSessionId(),
                history.getUserInput(),
                history.getIntent(),
                history.getExecuteResult(),
                history.getCreatedAt());
    }

    private AiHistoryDetailResponse toDetailResponse(ChatHistory history) {
        return new AiHistoryDetailResponse(
                history.getId(),
                history.getSessionId(),
                history.getUserInput(),
                history.getAiResponse(),
                history.getIntent(),
                history.getExtractedInfo(),
                history.getExecuteResult(),
                history.getErrorMessage(),
                history.getLlmModel(),
                history.getResponseTimeMs(),
                history.getCreatedAt());
    }
}
