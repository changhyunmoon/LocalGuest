package com.team6.apiserver.auth.config;

import com.team6.apiserver.auth.oauth.OAuth2CallbackRoleFromRedisFilter;
import com.team6.apiserver.auth.oauth.OAuth2RoleHintCookieFilter;
import com.team6.apiserver.auth.oauth.RoleStoringOAuth2AuthorizationRequestResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

@Configuration
public class OAuth2AuthorizationConfig {

    private static final String OAUTH2_AUTHORIZATION_BASE_URI = "/oauth2/authorization";

    @Bean
    public OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> memberRedisTemplate) {
        return new RoleStoringOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                OAUTH2_AUTHORIZATION_BASE_URI,
                memberRedisTemplate);
    }

    @Bean
    public OAuth2RoleHintCookieFilter oauth2RoleHintCookieFilter() {
        return new OAuth2RoleHintCookieFilter();
    }

    @Bean
    public OAuth2CallbackRoleFromRedisFilter oauth2CallbackRoleFromRedisFilter(
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> memberRedisTemplate) {
        return new OAuth2CallbackRoleFromRedisFilter(memberRedisTemplate);
    }
}
