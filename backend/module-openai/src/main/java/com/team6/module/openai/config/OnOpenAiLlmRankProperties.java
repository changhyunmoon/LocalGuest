package com.team6.module.openai.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@code localguest.ai.llm-rank-enabled=true} 이고 {@code localguest.ai.llm-provider=openai}(기본)일 때만 참.
 */
public final class OnOpenAiLlmRankProperties implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("localguest.ai.llm-rank-enabled", "false");
        if (!Boolean.parseBoolean(enabled == null ? "false" : enabled.trim())) {
            return false;
        }
        String provider = context.getEnvironment().getProperty("localguest.ai.llm-provider", "openai");
        return "openai".equalsIgnoreCase((provider == null ? "openai" : provider).trim());
    }
}
