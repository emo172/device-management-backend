package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.dto.ai.AiHistoryDetailResponse;
import com.jhun.backend.dto.ai.AiHistorySummaryResponse;
import com.jhun.backend.entity.ChatHistory;
import com.jhun.backend.mapper.ChatHistoryMapper;
import com.jhun.backend.service.AiService;
import com.jhun.backend.service.support.ai.RuleBasedAiProvider;
import com.jhun.backend.service.support.ai.RuleBasedAiResult;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 对话服务实现。
 * <p>
 * 当前阶段仅提供规则降级 / mock provider 能力，用于支撑 AI 对话入口和历史回放；
 * 所有响应都只落历史表，不直接修改预约、设备或其他业务表，确保 AI 不越过既有 Service 边界。
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
    private final RuleBasedAiProvider ruleBasedAiProvider;

    public AiServiceImpl(ChatHistoryMapper chatHistoryMapper, RuleBasedAiProvider ruleBasedAiProvider) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.ruleBasedAiProvider = ruleBasedAiProvider;
    }

    /**
     * 处理一轮 AI 文本对话并写入历史记录。
     * <p>
     * 该方法首先校验当前角色必须为 `USER`，避免设备管理员或系统管理员从 AI 入口越过正式业务边界；
     * 随后调用规则降级 provider 产出固定意图结果，并把本轮输入、回复、意图、执行状态和耗时落到 `chat_history`，
     * 作为历史页和后续 AI 能力排查的唯一过程留痕。当前阶段副作用仅限写入 AI 历史表，不会直接修改预约、设备等业务表。
     *
     * @param userId 当前登录用户 ID，用于绑定历史归属
     * @param role 当前登录角色，用于执行 AI 使用边界校验
     * @param request 对话请求，包含可选会话 ID 与用户输入文本
     * @return 本轮对话的响应结果，包含历史 ID、会话 ID、意图、执行状态和 AI 回复
     */
    @Override
    @Transactional
    public AiChatResponse chat(String userId, String role, AiChatRequest request) {
        validateAiRole(role);
        long startedAt = System.currentTimeMillis();
        RuleBasedAiResult result = ruleBasedAiProvider.process(request.message());

        ChatHistory history = new ChatHistory();
        history.setId(UuidUtil.randomUuid());
        history.setUserId(userId);
        history.setSessionId(resolveSessionId(request.sessionId()));
        history.setUserInput(request.message().trim());
        history.setAiResponse(result.aiResponse());
        history.setIntent(result.intent());
        history.setIntentConfidence(result.intentConfidence());
        history.setExtractedInfo(result.extractedInfo());
        history.setExecuteResult(result.executeResult());
        history.setErrorMessage(result.errorMessage());
        history.setLlmModel(result.providerName());
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
