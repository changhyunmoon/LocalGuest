package com.team6.apiserver.auth.oauth;

import com.team6.domain.auth.provider.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.frontend-redirect-base:http://localhost:5173}")
    private String frontendRedirectBase;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {
        //인증된 사용자에게서 이메일 추출
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        boolean newGuest = Boolean.TRUE.equals(request.getAttribute(CustomOauth2UserService.ATTR_SOCIAL_IS_NEW_GUEST));
        boolean withOnboardingClaim = newGuest && "ROLE_GUEST".equals(role);

        // JWT 발급 (최초 소셜 가입 GUEST — 여행 성향 안내)
        String token = jwtTokenProvider.createToken(email, role, withOnboardingClaim);

        String base = frontendRedirectBase.replaceAll("/+$", "");
        String redirectUrl = UriComponentsBuilder
                .fromHttpUrl(base + "/oauth2/callback")
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        clearRoleHintCookie(request, response);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private void clearRoleHintCookie(HttpServletRequest request, HttpServletResponse response) {
        String ctx = request.getContextPath();
        String p = (ctx == null || ctx.isEmpty()) ? "/" : ctx;
        ResponseCookie clear = ResponseCookie.from(OAuth2RoleHintCookieFilter.OAUTH_ROLE_HINT_COOKIE, "")
                .path(p)
                .httpOnly(true)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
    }
}
