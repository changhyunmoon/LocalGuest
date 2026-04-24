package com.team6.apiserver.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * OAuth2 시작 시 URL의 {@code ?role=GUEST|GUIDE} 를 읽어, 구글이 돌려주는 {@code state} 값과 함께 Redis에 잠시 저장한다.
 * 콜백(8080 등)에서 {@link CustomOauth2UserService}가 동일 {@code state}로 꺼내 쓰므로,
 * 프론트(5173) 쿠키가 콜백 요청에 안 실리는 문제를 피한다.
 */
@Slf4j
public class RoleStoringOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String REDIS_KEY_PREFIX = "localguest:oauth2:role:";
    /**
     * 회원가입 Step1 "Google로 빠르게" 등 — 로그인이 아닌 <strong>최초 GUEST</strong> 자동가입을 허용할 때만 설정.
     */
    static final String REDIS_GUEST_SOCIAL_SIGNUP_PREFIX = "localguest:oauth2:guestSocialSignup:";

    static final Duration ROLE_TTL = Duration.ofMinutes(15);

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final RedisTemplate<String, String> redisTemplate;

    public RoleStoringOAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            String authorizationRequestBaseUri,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                authorizationRequestBaseUri);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest auth = defaultResolver.resolve(request);
        storeRoleIfNeeded(request, auth);
        return auth;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest auth = defaultResolver.resolve(request, clientRegistrationId);
        storeRoleIfNeeded(request, auth);
        return auth;
    }

    private void storeRoleIfNeeded(HttpServletRequest request, OAuth2AuthorizationRequest auth) {
        if (auth == null) {
            return;
        }
        String state = auth.getState();
        if (!StringUtils.hasText(state)) {
            return;
        }
        String roleParam = request.getParameter("role");
        if (!StringUtils.hasText(roleParam)) {
            return;
        }
        String normalized = roleParam.trim().equalsIgnoreCase("GUIDE") ? "GUIDE" : "GUEST";
        String key = REDIS_KEY_PREFIX + state;
        try {
            redisTemplate.opsForValue().set(key, normalized, ROLE_TTL);
        } catch (Exception e) {
            log.warn("[OAuth2] Redis에 role 저장 실패: statePrefix={}…", state.substring(0, Math.min(8, state.length())), e);
        }
        if ("GUEST".equals(normalized) && "1".equals(request.getParameter("signup"))) {
            String signupKey = REDIS_GUEST_SOCIAL_SIGNUP_PREFIX + state;
            try {
                redisTemplate.opsForValue().set(signupKey, "1", ROLE_TTL);
            } catch (Exception e) {
                log.warn(
                        "[OAuth2] Redis에 guestSocialSignup 저장 실패: statePrefix={}…",
                        state.substring(0, Math.min(8, state.length())),
                        e);
            }
        }
    }
}
