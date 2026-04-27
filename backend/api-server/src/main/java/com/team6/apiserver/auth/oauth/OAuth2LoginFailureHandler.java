package com.team6.apiserver.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 조건 실패 — {@code /auth/login?authError=1&reason=...} (프론트 모달)
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.frontend-redirect-base:http://localhost:5173}")
    private String frontendRedirectBase;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String code = "oauth_failed";
        if (exception instanceof OAuth2AuthenticationException oae && oae.getError() != null) {
            if (StringUtils.hasText(oae.getError().getErrorCode())) {
                code = oae.getError().getErrorCode();
            }
        }
        String base = frontendRedirectBase.replaceAll("/+$", "");
        String target =
                base
                        + "/auth/login?authError=1&reason="
                        + URLEncoder.encode(code, StandardCharsets.UTF_8);
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.FOUND.value());
        response.sendRedirect(target);
    }
}
