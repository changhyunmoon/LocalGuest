package com.team6.module.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiRecommendationTuning#POLICY_VERSION} 문자열 계약(로그·헤더·캐시 키 구분용).
 */
class PolicyVersionContractTest {

    @Test
    void policy_version_should_use_year_month_serial_format() {
        assertThat(AiRecommendationTuning.POLICY_VERSION).matches("\\d{4}\\.\\d{2}\\.\\d{1,2}");
    }
}
