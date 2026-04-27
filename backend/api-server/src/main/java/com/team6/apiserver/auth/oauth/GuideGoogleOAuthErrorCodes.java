package com.team6.apiserver.auth.oauth;

/**
 * 구글 OAuth2 실패 시 {@link org.springframework.security.oauth2.core.OAuth2Error#getErrorCode()} — 프론트 <code>reason</code>과 동일.
 * (가이드 전용 외: 여행자·역할누락·비활성 등)
 */
public final class GuideGoogleOAuthErrorCodes {
    public static final String NOT_MEMBER = "guide_oauth_not_member";
    /** GUEST(회원)이나 승인·활성 가이드 프로필 없음 */
    public static final String NOT_GUIDE = "guide_oauth_not_registered";
    /** Redis/쿠키/필터에서 역할을 복구하지 못함(기본 GUEST로 자동가입하는 것 방지) */
    public static final String ROLE_MISSING = "oauth_role_missing";
    /** (여행자) 구글 로그인 — member에 동일 이메일 GUEST가 없으면(사전 회원가입 없음) */
    public static final String GUEST_OAUTH_NOT_REGISTERED = "guest_oauth_not_registered";
    public static final String MEMBER_INACTIVE = "oauth_member_inactive";
    public static final String INVALID_OAUTH_USER = "invalid_user";

    private GuideGoogleOAuthErrorCodes() {
    }
}
