package com.team6.module.gemini.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@code localguest.ai.llm-prompt-extraction-enabled=true} 이고 {@code localguest.ai.llm-provider=gemini}일 때만 참.
 */
public final class OnGeminiLlmExtractionProperties implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty("localguest.ai.llm-prompt-extraction-enabled", "false"))) {
            return false;
        }
        return "gemini".equalsIgnoreCase(env.getProperty("localguest.ai.llm-provider", "openai"));
    }
}
