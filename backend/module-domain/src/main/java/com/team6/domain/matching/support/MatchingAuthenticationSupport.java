package com.team6.domain.matching.support;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 매칭 API용 인증: JWT의 ROLE 과 DB {@code member.roles} 를 맞춘다.
 * <p>한 계정(email)당 Member 한 행 — Guest/Guide 역할 동시 보유 가능.
 * API별로 쓰는 식별자: 게스트 흐름은 {@code member.id}, 가이드 흐름은 가이드 작업 기준으로 {@code guide_profiles.id}
 * ({@code MatchRequest#guideId}와 동일)를 사용한다.</p>
 */
@Component
@RequiredArgsConstructor
public class MatchingAuthenticationSupport {

    private final MemberRepository memberRepository;
    private final GuideProfileRepository guideProfileRepository;

    /**
     * SecurityContext의 권한에서 JWT에 실린 역할을 읽는다. (예: ROLE_GUEST, ROLE_GUIDE)
     */
    public Role resolveTokenRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            Role r = mapAuthorityToRole(authority.getAuthority());
            if (r != null) {
                return r;
            }
        }
        throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
    }

    private Role mapAuthorityToRole(String authority) {
        if (authority == null) {
            return null;
        }
        return switch (authority) {
            case "ROLE_GUEST" -> Role.GUEST;
            case "ROLE_GUIDE" -> Role.GUIDE;
            default -> null;
        };
    }

    public Long getCurrentGuestMemberId() {
        String email = SecurityUtil.getCurrentUserEmail();
        if (resolveTokenRole() != Role.GUEST) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return memberRepository.findByEmail(email)
                .filter(m -> m.hasRole(Role.GUEST))
                .map(Member::getId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    public Long getCurrentGuideProfileId() {
        String email = SecurityUtil.getCurrentUserEmail();
        if (resolveTokenRole() != Role.GUIDE) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        Long memberId = memberRepository.findByEmail(email)
                .filter(m -> m.hasRole(Role.GUIDE))
                .map(Member::getId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
        return guideProfileRepository.findByMemberId(memberId)
                .map(guideProfile -> guideProfile.getId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    /**
     * TourExtension 조회: 게스트는 member id, 가이드는 guide_profiles id 를 반환한다.
     */
    public Long resolveTourExtensionActorId() {
        String email = SecurityUtil.getCurrentUserEmail();
        Role role = resolveTokenRole();
        if (role == Role.GUEST) {
            return memberRepository.findByEmail(email)
                    .filter(m -> m.hasRole(Role.GUEST))
                    .map(Member::getId)
                    .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
        }
        if (role == Role.GUIDE) {
            Long memberId = memberRepository.findByEmail(email)
                    .filter(m -> m.hasRole(Role.GUIDE))
                    .map(Member::getId)
                    .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
            return guideProfileRepository.findByMemberId(memberId)
                    .map(guideProfile -> guideProfile.getId())
                    .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
        }
        throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
    }
}
