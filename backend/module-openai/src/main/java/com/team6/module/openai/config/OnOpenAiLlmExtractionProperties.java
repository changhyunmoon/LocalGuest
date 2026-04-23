package com.team6.module.openai.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@code localguest.ai.llm-prompt-extraction-enabled=true} 이고 {@code localguest.ai.llm-provider=openai}(기본)일 때만 참.
 */
public final class OnOpenAiLlmExtractionProperties implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty("localguest.ai.llm-prompt-extraction-enabled", "false"))) {
            return false;
        }
        return "openai".equalsIgnoreCase(env.getProperty("localguest.ai.llm-provider", "openai"));
    }
}
