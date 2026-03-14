package com.jhun.backend.service;

import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.dto.ai.AiHistoryDetailResponse;
import com.jhun.backend.dto.ai.AiHistorySummaryResponse;
import java.util.List;

/**
 * AI 对话服务。
 */
public interface AiService {

    /**
     * 发起一轮 AI 文本对话。
     * <p>
     * 该方法只允许普通用户调用，设备管理员和系统管理员不得通过 AI 入口绕过正式业务流程；
     * 服务实现需要完成意图识别、回复生成与历史落库，并保证 AI 只记录过程信息而不直接修改业务表。
     *
     * @param userId 当前登录用户 ID，用于绑定历史归属
     * @param role 当前登录角色，用于执行 USER 角色边界校验
     * @param request 对话请求，包含会话 ID 和用户输入文本
     * @return 本轮 AI 对话结果，包含历史 ID、意图、执行结果与回复内容
     */
    AiChatResponse chat(String userId, String role, AiChatRequest request);

    /**
     * 查询当前用户的 AI 历史列表。
     * <p>
     * 该方法仅返回当前登录普通用户自己的历史记录，防止跨用户读取对话内容；
     * 返回结果用于历史页列表展示，不应产生任何写入副作用。
     *
     * @param userId 当前登录用户 ID
     * @param role 当前登录角色，用于执行 USER 角色边界校验
     * @return 当前用户可见的 AI 历史列表
     */
    List<AiHistorySummaryResponse> listHistory(String userId, String role);

    /**
     * 查询当前用户的一条 AI 历史详情。
     * <p>
     * 该方法要求记录归属人与当前登录用户一致，否则必须阻止读取并抛出业务异常；
     * 返回结果用于前端详情回放，包含结构化信息、执行状态和错误信息等完整上下文。
     *
     * @param userId 当前登录用户 ID，用于校验历史归属
     * @param role 当前登录角色，用于执行 USER 角色边界校验
     * @param historyId 待查询历史记录的主键 ID
     * @return 指定历史记录的详情响应对象
     */
    AiHistoryDetailResponse getHistoryDetail(String userId, String role, String historyId);
}
