package com.team6.apiserver.auth.oauth;

import com.team6.domain.auth.provider.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException {
        //인증된 사용자에게서 이메일 추출
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        // JWT 발급
        String token = jwtTokenProvider.createToken(email, role);

        // 프론트엔드로 토큰과 함께 리다이렉트
        String redirectUrl = "http://localhost:3000/oauth2/callback?token=" + token;
        getRedirectStrategy().sendRedirect(request,response, redirectUrl);

        // 프론트 없을 때 임시 테스트용 → 토큰을 바로 응답으로 반환
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"token\": \"" + token + "\"}");
    }
}
