package com.team6.module.openai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link LocalGuestOpenAiProperties}를 항상 바인딩한다(기본 {@code enabled=false} 포함).
 * 실제 OpenAI 빈은 {@link LocalGuestOpenAiConfiguration}에서만 조건부로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalGuestOpenAiProperties.class)
public class LocalGuestOpenAiPropertiesBootstrapConfiguration {
}
