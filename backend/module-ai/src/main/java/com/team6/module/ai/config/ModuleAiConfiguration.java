package com.team6.module.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link LocalGuestAiProperties} 한 객체에서 {@link ScoringPolicySnapshot}·{@link DiversityRerankSnapshot}을 만들어
 * 스코어·다양성·(integration 경로) 후보 풀 설정이 동일 YAML 소스를 보도록 한다.
 */
@Configuration
@EnableConfigurationProperties(LocalGuestAiProperties.class)
public class ModuleAiConfiguration {

    @Bean
    public ScoringPolicySnapshot scoringPolicySnapshot(LocalGuestAiProperties properties) {
        return ScoringPolicySnapshot.from(properties.getScoringPolicy());
    }

    @Bean
    public DiversityRerankSnapshot diversityRerankSnapshot(LocalGuestAiProperties properties) {
        return DiversityRerankSnapshot.from(properties.getDiversityRerank());
    }
}
