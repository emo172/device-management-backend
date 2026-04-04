package com.jhun.backend.unit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.config.ai.AiProperties;
import com.jhun.backend.config.ai.AiRuntimeProperties;
import com.jhun.backend.dto.ai.AiChatRequest;
import com.jhun.backend.mapper.ChatHistoryMapper;
import com.jhun.backend.service.AiService;
import com.jhun.backend.service.impl.AiServiceImpl;
import com.jhun.backend.service.support.ai.AiProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Provider 选择运行时测试。
 * <p>
 * 本测试只锁定当前任务要求的四个运行时分支：
 * 1) `ai.enabled=false` 时只拒绝新的对话请求；
 * 2) `ai.provider=mock` 时暴露 mock 运行时状态；
 * 3) `ai.provider=qwen` 且配置完整时暴露 qwen 运行时状态；
 * 4) `ai.provider=qwen` 但缺少必需配置时必须在上下文启动阶段 fail-fast。
 */
class AiProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    /**
     * 验证关闭 AI 开关后，只有新的 chat 入口会被服务层显式拒绝，且不会误触发 provider 或历史落库依赖。
     */
    @Test
    void shouldRejectNewChatWhenAiDisabled() {
        contextRunner
                .withPropertyValues(
                        "ai.enabled=false",
                        "ai.provider=mock",
                        "ai.timeout-seconds=30")
                .run(context -> {
                    AiService aiService = context.getBean(AiService.class);
                    AiRuntimeProperties aiRuntimeProperties = context.getBean(AiRuntimeProperties.class);
                    AiProvider aiProvider = context.getBean(AiProvider.class);

                    assertThat(aiRuntimeProperties.isChatEnabled()).isFalse();
                    assertThat(aiRuntimeProperties.provider()).isEqualTo(AiProperties.Provider.MOCK);
                    /*
                     * Provider 类型在服务初始化阶段会被读取一次，用于建立 `provider -> implementation` 索引；
                     * 这里先清空该初始化交互，再验证“ai.enabled=false 时不会真正处理消息”这条业务契约。
                     */
                    clearInvocations(aiProvider);

                    assertThatThrownBy(() -> aiService.chat("user-1", "USER", new AiChatRequest(null, "查询一下我的预约")))
                            .isInstanceOf(BusinessException.class)
                            .hasMessage("AI 对话功能当前已关闭，请联系管理员开启 ai.enabled 后再试");

                    verifyNoInteractions(context.getBean(ChatHistoryMapper.class));
                    verify(aiProvider, never()).process(anyString(), anyString(), anyString());
                });
    }

    /**
     * 验证 mock provider 仍然是合法显式状态，后续真实 provider 接入前本地回退仍有统一抓手可读。
     */
    @Test
    void shouldExposeMockProviderRuntimeState() {
        contextRunner
                .withPropertyValues(
                        "ai.enabled=true",
                        "ai.provider=mock",
                        "ai.timeout-seconds=45")
                .run(context -> {
                    AiRuntimeProperties aiRuntimeProperties = context.getBean(AiRuntimeProperties.class);
                    assertThat(aiRuntimeProperties.isChatEnabled()).isTrue();
                    assertThat(aiRuntimeProperties.provider()).isEqualTo(AiProperties.Provider.MOCK);
                    assertThat(aiRuntimeProperties.requestTimeout()).isEqualTo(Duration.ofSeconds(45));
                });
    }

    /**
     * 验证 qwen provider 在当前任务阶段至少已经成为可辨识的正式运行时状态，供后续 HTTP 客户端和 provider 实现复用。
     */
    @Test
    void shouldExposeQwenProviderRuntimeState() {
        contextRunner
                .withPropertyValues(
                        "ai.enabled=true",
                        "ai.provider=qwen",
                        "ai.timeout-seconds=60",
                        "ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "ai.qwen.model=qwen-plus",
                        "ai.qwen.api-key=test-key",
                        "ai.qwen.max-tool-calls=1")
                .run(context -> {
                    AiRuntimeProperties aiRuntimeProperties = context.getBean(AiRuntimeProperties.class);
                    assertThat(aiRuntimeProperties.provider()).isEqualTo(AiProperties.Provider.QWEN);
                    assertThat(aiRuntimeProperties.requestTimeout()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(aiRuntimeProperties.qwen().baseUrl())
                            .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
                    assertThat(aiRuntimeProperties.qwen().model()).isEqualTo("qwen-plus");
                    assertThat(aiRuntimeProperties.qwen().maxToolCalls()).isEqualTo(1);
                });
    }

    /**
     * 验证选中 qwen 但缺少必需配置时会在上下文创建阶段直接失败，避免把错误拖延到第一条真实请求才暴露。
     */
    @Test
    void shouldFailFastWhenQwenRequiredConfigIsMissing() {
        contextRunner
                .withPropertyValues(
                        "ai.enabled=true",
                        "ai.provider=qwen",
                        "ai.timeout-seconds=30",
                        "ai.qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "ai.qwen.model=qwen-plus",
                        "ai.qwen.api-key=")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ai.qwen.api-key")
                            .hasMessageContaining("必须配置");
                });
    }

    /**
     * 仅为当前任务组装最小运行时 Bean。
     * <p>
     * 这里故意使用 Mockito 桩替代数据库与 mock provider 真实实现，确保测试聚焦“配置绑定 + provider 选择 + disabled 守卫”本身，
     * 不把 Prompt 模板、Mapper 行为或历史落库细节掺进本任务的验收范围。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    static class TestConfiguration {

        @Bean
        AiRuntimeProperties aiRuntimeProperties(AiProperties aiProperties) {
            return new AiRuntimeProperties(aiProperties);
        }

        @Bean
        ChatHistoryMapper chatHistoryMapper() {
            return mock(ChatHistoryMapper.class);
        }

        @Bean
        AiProvider aiProvider() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.provider()).thenReturn(AiProperties.Provider.MOCK);
            return aiProvider;
        }

        @Bean
        AiService aiService(
                ChatHistoryMapper chatHistoryMapper,
                List<AiProvider> aiProviders,
                AiRuntimeProperties aiRuntimeProperties) {
            return new AiServiceImpl(chatHistoryMapper, aiProviders, aiRuntimeProperties);
        }
    }
}
