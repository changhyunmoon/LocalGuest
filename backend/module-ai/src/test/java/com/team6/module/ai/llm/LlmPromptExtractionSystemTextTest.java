package com.team6.module.ai.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptExtractionSystemTextTest {

    @Test
    void koreanJsonExtractor_requiresGuideFacingSummary() {
        String t = LlmPromptExtractionSystemText.KOREAN_JSON_EXTRACTOR;
        assertThat(t).contains("specialRequests");
        assertThat(t).contains("guideBullets");
        assertThat(t).contains("220");
        assertThat(t).contains("한라산");
    }
}
