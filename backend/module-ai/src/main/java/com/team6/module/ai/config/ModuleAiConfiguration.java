package com.team6.module.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocalGuestAiProperties.class)
public class ModuleAiConfiguration {
}
