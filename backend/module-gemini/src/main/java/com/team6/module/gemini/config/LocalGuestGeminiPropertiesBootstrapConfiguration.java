package com.team6.module.gemini.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link LocalGuestGeminiProperties}를 항상 바인딩한다(기본 {@code enabled=false}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalGuestGeminiProperties.class)
public class LocalGuestGeminiPropertiesBootstrapConfiguration {
}
