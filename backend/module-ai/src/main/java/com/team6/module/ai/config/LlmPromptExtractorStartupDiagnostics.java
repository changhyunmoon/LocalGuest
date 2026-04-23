package com.team6.module.ai.config;

import com.team6.module.ai.spi.LlmPromptExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 부팅 시 {@link LlmPromptExtractor} 주입 여부와 {@code localguest.ai.llm-provider} 설정을 한 줄로 남긴다.
 */
@Component
@Slf4j
public class LlmPromptExtractorStartupDiagnostics implements ApplicationRunner {

    private final LocalGuestAiProperties aiProperties;
    @Nullable
    private final LlmPromptExtractor llmPromptExtractor;

    public LlmPromptExtractorStartupDiagnostics(
            LocalGuestAiProperties aiProperties,
            @Autowired(required = false) @Nullable LlmPromptExtractor llmPromptExtractor
    ) {
        this.aiProperties = aiProperties;
        this.llmPromptExtractor = llmPromptExtractor;
    }

    @Override
    public void run(ApplicationArguments args) {
        String p = aiProperties.getLlmProvider();
        String provider = p == null || p.isBlank() ? "unknown" : p.trim().toLowerCase(Locale.ROOT);
        String bean = llmPromptExtractor == null ? "none" : llmPromptExtractor.getClass().getSimpleName();
        log.info(
                "[AI_LLM] startup llmPromptExtractionEnabled={} llmProviderCfg={} llmPromptExtractorBean={}",
                aiProperties.isLlmPromptExtractionEnabled(),
                provider,
                bean
        );
    }
}
