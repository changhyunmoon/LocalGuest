package com.team6.module.common.global.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    // [원리] 모든 모듈에서 '현재 로그인한 유저'라는 공통 정보를 안전하게 꺼내오는 도구입니다.
    public static String getCurrentUserEmail() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        return authentication.getName();
    }
}