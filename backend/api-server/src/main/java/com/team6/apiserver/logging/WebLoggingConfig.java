package com.team6.apiserver.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebLoggingConfig implements WebMvcConfigurer {

    private final HttpHandlerLoggingInterceptor httpHandlerLoggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpHandlerLoggingInterceptor).addPathPatterns("/**");
    }
}
