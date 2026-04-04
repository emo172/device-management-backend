package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.dto.ai.AiChatResponse;
import com.jhun.backend.entity.ChatHistory;
import com.jhun.backend.mapper.ChatHistoryMapper;
import com.jhun.backend.service.impl.AiServiceImpl;
import com.jhun.backend.service.support.ai.AiProvider;
import com.jhun.backend.service.support.ai.AiProviderResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link AiServiceImpl} 单元测试。
 * <p>
 * 本测试专门锁定 task 7 的服务层职责：
 * 1) `SUCCESS/PENDING/FAILED` 必须按 provider 统一结果稳定落到 `chat_history.execute_result`；
 * 2) `device_id/reservation_id/llm_model/extracted_info` 只能写入 provider 已经唯一解析出的值；
 * 3) 历史初始化必须先于 provider 执行，避免真实副作用先发生后再因为留痕失败报错；
 * 4) 最终历史回填失败只能降级记录，不能制造一次“业务已成功但接口失败”的假失败；
 * 5) `ai.enabled` 聊天守卫仍只作用于 chat 入口，不误触发 provider 或历史落库。
 */
class AiServiceImplTest {

    private static final String USER_ID = "user-1";
    private static final String USER_ROLE = "USER";

    private final ChatHistoryMapper chatHistoryMapper = mock(ChatHistoryMapper.class);
    private final AiProvider aiProvider = mock(AiProvider.class);

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        when(aiProvider.provider()).thenReturn(AiProperties.Provider.QWEN);
        aiService = new AiServiceImpl(
                chatHistoryMapper,
                List.of(aiProvider),
                runtimeProperties(true, AiProperties.Provider.QWEN));
    }

    /**
     * 验证成功的 qwen 路径会把结构化结果、精确模型名与唯一资源 ID 一并落库，且响应耗时覆盖整轮 provider 编排。
     */
    @Test
    void shouldPersistSuccessHistoryWithResolvedResourcesAndQwenModel() throws Exception {
        when(aiProvider.process(eq(USER_ID), eq(USER_ROLE), eq("帮我查一下预约 RES-1")))
                .thenAnswer(invocation -> {
                    Thread.sleep(20L);
                    return new AiProviderResult(
                            "QUERY",
                            BigDecimal.valueOf(0.98),
                            "{\"intent\":\"QUERY\",\"resolvedReservationId\":\"RES-1\"}",
                            "SUCCESS",
                            "已查询到预约 RES-1",
                            null,
                            "qwen-plus",
                            "dev-1",
                            "RES-1");
                });

        AiChatResponse response = aiService.chat(USER_ID, USER_ROLE, new AiChatRequest("session-1", "帮我查一下预约 RES-1"));

        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryMapper).insert(any(ChatHistory.class));
        verify(chatHistoryMapper).updateById(historyCaptor.capture());
        ChatHistory history = historyCaptor.getValue();
        assertThat(response.sessionId()).isEqualTo("session-1");
        assertThat(response.intent()).isEqualTo("QUERY");
        assertThat(response.executeResult()).isEqualTo("SUCCESS");
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getSessionId()).isEqualTo("session-1");
        assertThat(history.getUserInput()).isEqualTo("帮我查一下预约 RES-1");
        assertThat(history.getExecuteResult()).isEqualTo("SUCCESS");
        assertThat(history.getExtractedInfo()).isEqualTo("{\"intent\":\"QUERY\",\"resolvedReservationId\":\"RES-1\"}");
        assertThat(history.getLlmModel()).isEqualTo("qwen-plus");
        assertThat(history.getDeviceId()).isEqualTo("dev-1");
        assertThat(history.getReservationId()).isEqualTo("RES-1");
        assertThat(history.getErrorMessage()).isNull();
        assertThat(history.getResponseTimeMs()).isGreaterThanOrEqualTo(15);
        assertThat(history.getCreatedAt()).isNotNull();
    }

    /**
     * 验证当 provider 判定为待补充信息时，服务层会保留 PENDING 语义并且只落库已唯一解析成功的资源字段。
     */
    @Test
    void shouldPersistPendingHistoryWithoutGuessingUnresolvedReservationId() {
        when(aiProvider.process(eq(USER_ID), eq(USER_ROLE), eq("帮我预约投影仪")))
                .thenReturn(new AiProviderResult(
                        "RESERVE",
                        BigDecimal.valueOf(0.91),
                        "{\"intent\":\"RESERVE\",\"missingFields\":[\"startTime\",\"endTime\"]}",
                        "PENDING",
                        "请补充开始时间和结束时间。",
                        "请补充开始时间和结束时间。",
                        "qwen-plus",
                        "dev-2",
                        null));

        aiService.chat(USER_ID, USER_ROLE, new AiChatRequest("session-2", "帮我预约投影仪"));

        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryMapper).insert(any(ChatHistory.class));
        verify(chatHistoryMapper).updateById(historyCaptor.capture());
        ChatHistory history = historyCaptor.getValue();
        assertThat(history.getExecuteResult()).isEqualTo("PENDING");
        assertThat(history.getDeviceId()).isEqualTo("dev-2");
        assertThat(history.getReservationId()).isNull();
        assertThat(history.getErrorMessage()).isEqualTo("请补充开始时间和结束时间。");
    }

    /**
     * 验证当 provider 返回业务拒绝或工具失败时，服务层会把 FAILED 语义和错误信息稳定落库，且不会擅自猜测资源主键。
     */
    @Test
    void shouldPersistFailedHistoryAndKeepUnknownResourcesNull() {
        when(aiProvider.process(eq(USER_ID), eq(USER_ROLE), eq("帮我取消今天的预约")))
                .thenReturn(new AiProviderResult(
                        "CANCEL",
                        BigDecimal.valueOf(0.87),
                        "{\"intent\":\"CANCEL\",\"toolName\":\"cancel_my_reservation\"}",
                        "FAILED",
                        "AI 工具调用失败：开始前 24 小时内取消需管理员处理",
                        "开始前 24 小时内取消需管理员处理",
                        "qwen-plus",
                        null,
                        null));

        AiChatResponse response = aiService.chat(USER_ID, USER_ROLE, new AiChatRequest(null, "帮我取消今天的预约"));

        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryMapper).insert(any(ChatHistory.class));
        verify(chatHistoryMapper).updateById(historyCaptor.capture());
        ChatHistory history = historyCaptor.getValue();
        assertThat(response.executeResult()).isEqualTo("FAILED");
        assertThat(history.getExecuteResult()).isEqualTo("FAILED");
        assertThat(history.getErrorMessage()).isEqualTo("开始前 24 小时内取消需管理员处理");
        assertThat(history.getDeviceId()).isNull();
        assertThat(history.getReservationId()).isNull();
        assertThat(history.getSessionId()).isNotBlank();
    }

    /**
     * 验证初始历史骨架如果无法落库，会在 provider 执行前就直接失败，避免真实业务副作用先发生后才发现无法留痕。
     */
    @Test
    void shouldStopBeforeProviderWhenPendingHistoryInsertFails() {
        doThrow(new RuntimeException("chat history insert failed"))
                .when(chatHistoryMapper)
                .insert(any(ChatHistory.class));

        assertThatThrownBy(() -> aiService.chat(USER_ID, USER_ROLE, new AiChatRequest("session-3", "帮我取消预约")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 对话历史初始化失败，已阻止后续工具执行");

        verify(aiProvider, never()).process(any(), any(), any());
        verify(chatHistoryMapper, never()).updateById(any(ChatHistory.class));
    }

    /**
     * 验证当 provider 已经产出最终结果时，历史回填失败只会降级记录而不会把接口结果打成失败。
     */
    @Test
    void shouldReturnProviderResultWhenFinalHistoryUpdateFails() {
        when(aiProvider.process(eq(USER_ID), eq(USER_ROLE), eq("帮我查一下我的预约")))
                .thenReturn(new AiProviderResult(
                        "QUERY",
                        BigDecimal.valueOf(0.95),
                        "{\"intent\":\"QUERY\"}",
                        "SUCCESS",
                        "已查询到你的预约。",
                        null,
                        "qwen-plus",
                        null,
                        null));
        doThrow(new RuntimeException("chat history update failed"))
                .when(chatHistoryMapper)
                .updateById(any(ChatHistory.class));

        AiChatResponse response = aiService.chat(USER_ID, USER_ROLE, new AiChatRequest("session-4", "帮我查一下我的预约"));

        verify(chatHistoryMapper).insert(any(ChatHistory.class));
        verify(chatHistoryMapper).updateById(any(ChatHistory.class));
        assertThat(response.executeResult()).isEqualTo("SUCCESS");
        assertThat(response.aiResponse()).isEqualTo("已查询到你的预约。");
    }

    /**
     * 验证 `ai.enabled=false` 时服务层只拒绝新的聊天请求，不会误触发 provider 或写历史。
     */
    @Test
    void shouldRejectChatWhenDisabledWithoutCallingProvider() {
        AiServiceImpl disabledService = new AiServiceImpl(
                chatHistoryMapper,
                List.of(aiProvider),
                runtimeProperties(false, AiProperties.Provider.QWEN));

        assertThatThrownBy(() -> disabledService.chat(USER_ID, USER_ROLE, new AiChatRequest(null, "查询我的预约")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 对话功能当前已关闭，请联系管理员开启 ai.enabled 后再试");

        verify(aiProvider, never()).process(any(), any(), any());
        verify(chatHistoryMapper, never()).insert(any(ChatHistory.class));
    }

    private AiRuntimeProperties runtimeProperties(boolean enabled, AiProperties.Provider provider) {
        return new AiRuntimeProperties(new AiProperties(
                enabled,
                provider,
                30,
                new AiProperties.Qwen(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "qwen-plus",
                        "test-key",
                        1)));
    }
}
