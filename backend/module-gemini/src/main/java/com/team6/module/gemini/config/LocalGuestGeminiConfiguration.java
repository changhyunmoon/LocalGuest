package com.team6.module.gemini.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.spi.LlmPromptExtractor;
import com.team6.module.gemini.prompt.GeminiLlmPromptExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Gemini(Generative Language API) 클라이언트. {@code localguest.gemini.enabled=true} 일 때만 빈을 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "localguest.gemini", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LocalGuestGeminiConfiguration {

    @Bean
    public RestClient geminiGenerativeLanguageRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    @Bean
    @Conditional(OnGeminiLlmExtractionProperties.class)
    public LlmPromptExtractor geminiLlmPromptExtractor(
            RestClient geminiGenerativeLanguageRestClient,
            ObjectMapper objectMapper,
            LocalGuestGeminiProperties props,
            Environment env
    ) {
        String key = resolveApiKey(props, env);
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(
                    "localguest.gemini.enabled=true 이고 localguest.ai.llm-provider=gemini 인데 API 키가 없습니다. "
                            + "localguest.gemini.api-key, GEMINI_API_KEY, GOOGLE_API_KEY 중 하나를 설정하세요."
            );
        }
        return new GeminiLlmPromptExtractor(
                geminiGenerativeLanguageRestClient,
                objectMapper,
                props.getModel(),
                key.trim()
        );
    }

    private static String resolveApiKey(LocalGuestGeminiProperties props, Environment env) {
        if (StringUtils.hasText(props.getApiKey())) {
            return props.getApiKey().trim();
        }
        String g1 = env.getProperty("GEMINI_API_KEY");
        if (StringUtils.hasText(g1)) {
            return g1.trim();
        }
        String g2 = env.getProperty("GOOGLE_API_KEY");
        if (StringUtils.hasText(g2)) {
            return g2.trim();
        }
        return "";
    }
}
