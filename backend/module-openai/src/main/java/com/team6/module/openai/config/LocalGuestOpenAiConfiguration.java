package com.team6.module.openai.config;

import com.team6.module.ai.spi.LlmPromptExtractor;
import com.team6.module.openai.prompt.OpenAiLlmPromptExtractor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * OpenAI 클라이언트 빈 등록. 기본은 비활성({@code localguest.openai.enabled=false})이라 부팅에 영향이 없다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "localguest.openai", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LocalGuestOpenAiConfiguration {

    @Bean
    public OpenAiApi openAiApi(LocalGuestOpenAiProperties props, Environment env) {
        String resolved = resolveApiKey(props, env);
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalStateException(
                    "localguest.openai.enabled=true 인데 API 키가 없습니다. "
                            + "spring.ai.openai.api-key, SPRING_AI_OPENAI_API_KEY, localguest.openai.api-key 중 하나를 설정하세요."
            );
        }
        return OpenAiApi.builder()
                .apiKey(resolved)
                .restClientBuilder(RestClient.builder())
                .webClientBuilder(WebClient.builder())
                .responseErrorHandler(new DefaultResponseErrorHandler())
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder()
                .observationRegistry(ObservationRegistry.NOOP)
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
                .toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder().build())
                .build();
    }

    @Bean
    public RetryTemplate openAiRetryTemplate() {
        return new RetryTemplate();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            OpenAiApi openAiApi,
            LocalGuestOpenAiProperties props,
            ToolCallingManager toolCallingManager,
            RetryTemplate openAiRetryTemplate
    ) {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel(props.getModel());
        return new OpenAiChatModel(
                openAiApi,
                options,
                toolCallingManager,
                openAiRetryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean
    @ConditionalOnBean(OpenAiChatModel.class)
    @ConditionalOnProperty(prefix = "localguest.ai", name = "llm-prompt-extraction-enabled", havingValue = "true", matchIfMissing = false)
    public LlmPromptExtractor llmPromptExtractor(OpenAiChatModel chatModel) {
        return new OpenAiLlmPromptExtractor(chatModel);
    }

    private static String resolveApiKey(LocalGuestOpenAiProperties props, Environment env) {
        if (StringUtils.hasText(props.getApiKey())) {
            return props.getApiKey().trim();
        }
        String springAi = env.getProperty("spring.ai.openai.api-key");
        if (StringUtils.hasText(springAi)) {
            return springAi.trim();
        }
        String envVar = env.getProperty("SPRING_AI_OPENAI_API_KEY");
        if (StringUtils.hasText(envVar)) {
            return envVar.trim();
        }
        return "";
    }
}
