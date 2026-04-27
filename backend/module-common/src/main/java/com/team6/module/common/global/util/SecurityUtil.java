package com.team6.module.common.global.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    public static String getCurrentUserEmail() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || authentication.getName() == null) {
            throw new RuntimeException("인증 정보가 없습니다. ");
        }
        return authentication.getName();
    }

    public static String getCurrentUserRoleString() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || authentication.getAuthorities().isEmpty()) {
            throw new RuntimeException("권한 정보가 없습니다.");
        }
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}