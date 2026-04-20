package com.team6.domain.guide.support;

import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuideAuthenticationSupport {

    private final MemberRepository memberRepository;

    public Long getCurrentMemberId() {
        String email = SecurityUtil.getCurrentUserEmail();
        Role role = resolveTokenRole();
        return memberRepository.findByEmail(email)
                .filter(m -> m.hasRole(role))
                .map(Member::getId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.MEMBER_NOT_FOUND));
    }

    public Long getCurrentGuideMemberId() {
        String email = SecurityUtil.getCurrentUserEmail();
        if (resolveTokenRole() != Role.GUIDE) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }
        return memberRepository.findByEmail(email)
                .filter(m -> m.hasRole(Role.GUIDE))
                .map(Member::getId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED));
    }

    private Role resolveTokenRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if ("ROLE_GUEST".equals(value)) {
                return Role.GUEST;
            }
            if ("ROLE_GUIDE".equals(value)) {
                return Role.GUIDE;
            }
            if ("ROLE_ADMIN".equals(value)) {
                return Role.ADMIN;
            }
        }
        throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
    }
}
