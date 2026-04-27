package com.team6.apiserver.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /login/oauth2/code/* 콜백이 들어올 때 쿼리 {@code state}로 Redis에 저장된 role을 읽어
 * request attribute에 둔다. {@code CustomOauth2UserService#loadUser}가 호출될 때
 * {@code getParameter("state")}가 비어 role이 GUEST로만 잡히는 환경을 막는다.
 */
public class OAuth2CallbackRoleFromRedisFilter extends OncePerRequestFilter {

    public static final String ATTR_OAUTH2_ROLE_FROM_REDIS = "localguest.oauth2.roleFromRedis";
    /** 콜백 시 Redis — login 화면은 미설정, {@code /signup?signup=1} GUEST 흐름만 true */
    public static final String ATTR_OAUTH2_GUEST_SOCIAL_SIGNUP = "localguest.oauth2.guestSocialSignup";

    private final RedisTemplate<String, String> redisTemplate;

    public OAuth2CallbackRoleFromRedisFilter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String sp = request.getServletPath();
            if (sp != null && sp.startsWith("/login/oauth2/code/")) {
                String state = request.getParameter("state");
                if (StringUtils.hasText(state)) {
                    String key = RoleStoringOAuth2AuthorizationRequestResolver.REDIS_KEY_PREFIX + state;
                    try {
                        String fromRedis = redisTemplate.opsForValue().get(key);
                        if (StringUtils.hasText(fromRedis)) {
                            redisTemplate.delete(key);
                            String normalized = fromRedis.trim().equalsIgnoreCase("GUIDE")
                                    ? "GUIDE"
                                    : "GUEST";
                            request.setAttribute(ATTR_OAUTH2_ROLE_FROM_REDIS, normalized);
                        }
                    } catch (Exception ignored) {
                    }
                    String signupKey = RoleStoringOAuth2AuthorizationRequestResolver.REDIS_GUEST_SOCIAL_SIGNUP_PREFIX
                            + state;
                    try {
                        String s = redisTemplate.opsForValue().get(signupKey);
                        if (StringUtils.hasText(s) && "1".equals(s.trim())) {
                            redisTemplate.delete(signupKey);
                            request.setAttribute(ATTR_OAUTH2_GUEST_SOCIAL_SIGNUP, Boolean.TRUE);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
