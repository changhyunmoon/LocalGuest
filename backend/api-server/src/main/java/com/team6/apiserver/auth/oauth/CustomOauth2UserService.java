package com.team6.apiserver.auth.oauth;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Google userinfo로 이메일을 확정한 뒤 <strong>DB만</strong>으로 통과/거부({@code GuideGoogleOAuthErrorCodes} + ACTIVE 검사, 가이드는 GUEST+GUIDE+프로필).
 */
@Service
@Slf4j
public class CustomOauth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /**
     * {@code OAuth2SuccessHandler}에서 여행 성향(온보딩) JWT 클레임에 사용
     */
    public static final String ATTR_SOCIAL_IS_NEW_GUEST = "localguest.social.isNewGuest";

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final HttpServletRequest request;
    private final RedisTemplate<String, String> redisTemplate;

    public CustomOauth2UserService(
            MemberService memberService,
            MemberRepository memberRepository,
            GuideProfileRepository guideProfileRepository,
            HttpServletRequest request,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
        this.guideProfileRepository = guideProfileRepository;
        this.request = request;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String selectedRoleStr = resolveRoleForOAuth2Callback();
        if (selectedRoleStr == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            GuideGoogleOAuthErrorCodes.ROLE_MISSING,
                            "로그인 진행 정보(역할)를 찾을 수 없습니다. 쿠키·Redis·동일 오리진 설정을 확인한 뒤 다시 시도해 주세요.",
                            null));
        }
        boolean wantsGuide = "GUIDE".equalsIgnoreCase(selectedRoleStr);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String rawEmail = (String) attributes.get("email");
        String email = rawEmail == null ? null : rawEmail.trim();
        if (email != null && email.isEmpty()) {
            email = null;
        }
        final Member member;
        if (!wantsGuide) {
            member = resolveGuestForGoogleSocial(email, attributes);
        } else {
            if (!StringUtils.hasText(email)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                GuideGoogleOAuthErrorCodes.INVALID_OAUTH_USER,
                                "이메일이 없어 가이드로 로그인을 완료할 수 없습니다.",
                                null));
            }
            member = resolveGuideForGoogleOrThrow(email);
        }
        log.info(
                "[OAuth2] loadUser success: wantsGuide={} email={} memberId={} memberRole={}",
                wantsGuide,
                maskEmailForLog(rawEmail),
                member.getId(),
                member.getRole());
        return buildOAuth2User(oAuth2User, member);
    }

    private boolean fromSignupFlow() {
        return Boolean.TRUE.equals(
                request.getAttribute(OAuth2CallbackRoleFromRedisFilter.ATTR_OAUTH2_GUEST_SOCIAL_SIGNUP));
    }

    private static String maskEmailForLog(String email) {
        if (email == null || email.isEmpty()) {
            return "(none)";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String dom = email.substring(at);
        if (local.length() <= 2) {
            return "**" + dom;
        }
        return local.substring(0, 2) + "***" + dom;
    }

    private static void requireActive(Member m) {
        if (m.getStatus() != Status.ACTIVE) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            GuideGoogleOAuthErrorCodes.MEMBER_INACTIVE,
                            "탈퇴·비활성 계정은 로그인할 수 없습니다.",
                            null));
        }
    }

    private static OAuth2User buildOAuth2User(OAuth2User oAuth2User, Member member) {
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(member.getRole().getKey())),
                oAuth2User.getAttributes(),
                "email"
        );
    }

    private Member resolveGuestForGoogleSocial(String email, Map<String, Object> attributes) {
        if (fromSignupFlow()) {
            if (!StringUtils.hasText(email)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                GuideGoogleOAuthErrorCodes.INVALID_OAUTH_USER,
                                "이메일이 없는 소셜 응답입니다.",
                                null));
            }
            String name = (String) attributes.get("name");
            String picture = attributes.get("picture") instanceof String s ? s : null;
            MemberService.SocialMemberResult r =
                    memberService.findOrCreateMemberWithFlag(email, name, picture, Role.GUEST);
            if (r.created()) {
                request.setAttribute(ATTR_SOCIAL_IS_NEW_GUEST, Boolean.TRUE);
            }
            requireActive(r.member());
            return r.member();
        }
        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            GuideGoogleOAuthErrorCodes.INVALID_OAUTH_USER,
                            "이메일이 없는 소셜 응답입니다.",
                            null));
        }
        Member g =
                memberRepository
                        .findByEmailIgnoreCaseAndRole(email, Role.GUEST)
                        .orElseThrow(
                                () ->
                                        new OAuth2AuthenticationException(
                                                new OAuth2Error(
                                                        GuideGoogleOAuthErrorCodes.GUEST_OAUTH_NOT_REGISTERED,
                                                        "이 서비스에 (여행자) 회원가입이 완료된 이메일만 구글로 로그인할 수 있습니다. 먼저 회원가입을 해 주세요.",
                                                        null)));
        requireActive(g);
        return g;
    }

    /**
     * GUEST+ACTIVE, GUIDE+ACTIVE, guide_profiles(승인·활성). INSERT 없음.
     */
    private Member resolveGuideForGoogleOrThrow(String email) {
        Member guest =
                memberRepository
                        .findByEmailIgnoreCaseAndRole(email, Role.GUEST)
                        .orElseThrow(
                                () ->
                                        new OAuth2AuthenticationException(
                                                new OAuth2Error(
                                                        GuideGoogleOAuthErrorCodes.NOT_MEMBER,
                                                        "가이드로 로그인하려면 먼저 동일 이메일로 회원(여행자) 가입이 완료되어 있어야 합니다.",
                                                        null)));
        requireActive(guest);

        Member guide =
                memberRepository
                        .findByEmailIgnoreCaseAndRole(email, Role.GUIDE)
                        .orElseThrow(
                                () ->
                                        new OAuth2AuthenticationException(
                                                new OAuth2Error(
                                                        GuideGoogleOAuthErrorCodes.NOT_GUIDE,
                                                        "가이드 프로필(등록)이 완료·승인되지 않은 계정입니다.",
                                                        null)));
        requireActive(guide);
        if (!guideProfileRepository.hasApprovedAndActiveByMemberId(guide.getId())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            GuideGoogleOAuthErrorCodes.NOT_GUIDE,
                            "승인·활성 가이드 프로필이 없어 가이드로 로그인할 수 없습니다.",
                            null));
        }
        return guide;
    }

    private String resolveRoleForOAuth2Callback() {
        Object fromFilter = request.getAttribute(OAuth2CallbackRoleFromRedisFilter.ATTR_OAUTH2_ROLE_FROM_REDIS);
        if (fromFilter instanceof String s && StringUtils.hasText(s)) {
            return s.trim().equalsIgnoreCase("GUIDE") ? "GUIDE" : "GUEST";
        }
        String state = request.getParameter("state");
        if (StringUtils.hasText(state)) {
            String key = RoleStoringOAuth2AuthorizationRequestResolver.REDIS_KEY_PREFIX + state;
            try {
                String fromRedis = redisTemplate.opsForValue().get(key);
                if (StringUtils.hasText(fromRedis)) {
                    redisTemplate.delete(key);
                    return fromRedis.trim().equalsIgnoreCase("GUIDE") ? "GUIDE" : "GUEST";
                }
            } catch (Exception e) {
                log.debug("[OAuth2] Redis role 조회 실패", e);
            }
        }
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (OAuth2RoleHintCookieFilter.OAUTH_ROLE_HINT_COOKIE.equals(cookie.getName())
                        && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue().trim().equalsIgnoreCase("GUIDE") ? "GUIDE" : "GUEST";
                }
            }
        }
        return null;
    }
}
