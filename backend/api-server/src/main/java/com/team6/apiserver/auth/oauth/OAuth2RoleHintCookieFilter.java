package com.team6.apiserver.auth.oauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@code /oauth2/authorization/**?role=GUEST|GUIDE} 첫 요청(구글로 리다이렉트 직전)에
 * API 호스트({@code /api/...} 포함)에 HttpOnly 쿠키로 역할을 심는다. 브라우저 콜백 시
 * {@link CustomOauth2UserService}가 Redis/프론트 쿠키 없이도 role을 읽을 수 있게 해
 * (도메인/포트가 달라 document.cookie가 안 실리는 환경 보강).
 * {@code SecurityFilterChain}에만 등록한다.
 */
public class OAuth2RoleHintCookieFilter extends OncePerRequestFilter {

    public static final String OAUTH_ROLE_HINT_COOKIE = "localguest_oauth_role_hint";
    public static final int MAX_AGE_SEC = 600;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String path = request.getServletPath();
            if (path != null && path.startsWith("/oauth2/authorization/")) {
                String roleParam = request.getParameter("role");
                if (roleParam != null && !roleParam.isBlank()) {
                    String v = roleParam.trim().equalsIgnoreCase("GUIDE") ? "GUIDE" : "GUEST";
                    String ctx = request.getContextPath();
                    String pathAttr = (ctx == null || ctx.isEmpty()) ? "/" : ctx;
                    ResponseCookie c = ResponseCookie.from(OAUTH_ROLE_HINT_COOKIE, v)
                            .path(pathAttr)
                            .httpOnly(true)
                            .secure(request.isSecure())
                            .maxAge(MAX_AGE_SEC)
                            .sameSite("Lax")
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, c.toString());
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
