package com.team6.domain.matching.support;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingAuthenticationSupportTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private GuideProfileRepository guideProfileRepository;

    private MatchingAuthenticationSupport support;

    @BeforeEach
    void setUp() {
        support = new MatchingAuthenticationSupport(memberRepository, guideProfileRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(String email, String roleAuthority) {
        var auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority(roleAuthority))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Member baseMember(String email) {
        return Member.builder()
                .email(email)
                .password("pw")
                .name("name")
                .nickname("nick")
                .status(Status.ACTIVE)
                .roles(new HashSet<>())
                .build();
    }

    @Test
    void getCurrentGuestMemberId_findByEmail_thenRequiresGuestRole() {
        login("g@test.com", "ROLE_GUEST");
        Member m = baseMember("g@test.com");
        m.addRole(Role.GUEST);
        ReflectionTestUtils.setField(m, "id", 7L);
        when(memberRepository.findByEmail("g@test.com")).thenReturn(Optional.of(m));

        assertThat(support.getCurrentGuestMemberId()).isEqualTo(7L);
    }

    @Test
    void getCurrentGuestMemberId_rejectsWhenEmailExistsButNoGuestRole() {
        login("onlyguide@test.com", "ROLE_GUEST");
        Member m = baseMember("onlyguide@test.com");
        m.addRole(Role.GUIDE);
        when(memberRepository.findByEmail("onlyguide@test.com")).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> support.getCurrentGuestMemberId())
                .isInstanceOf(MatchingException.class);
    }

    @Test
    void getCurrentGuideProfileId_findByEmail_thenRequiresGuideRole_andMapsToProfileId() {
        login("gv@test.com", "ROLE_GUIDE");
        Member m = baseMember("gv@test.com");
        m.addRole(Role.GUIDE);
        ReflectionTestUtils.setField(m, "id", 99L);
        when(memberRepository.findByEmail("gv@test.com")).thenReturn(Optional.of(m));

        GuideProfile profile = GuideProfile.builder()
                .memberId(99L)
                .nickname("gn")
                .region("r")
                .build();
        ReflectionTestUtils.setField(profile, "id", 555L);
        when(guideProfileRepository.findByMemberId(99L)).thenReturn(Optional.of(profile));

        assertThat(support.getCurrentGuideProfileId()).isEqualTo(555L);
    }

    @Test
    void dualRole_sameMember_guestFlowAndGuideFlow() {
        Member both = baseMember("both@test.com");
        both.addRole(Role.GUEST);
        both.addRole(Role.GUIDE);
        ReflectionTestUtils.setField(both, "id", 100L);
        when(memberRepository.findByEmail("both@test.com")).thenReturn(Optional.of(both));

        login("both@test.com", "ROLE_GUEST");
        assertThat(support.resolveTourExtensionActorId()).isEqualTo(100L);

        GuideProfile profile = GuideProfile.builder()
                .memberId(100L)
                .nickname("g")
                .region("r")
                .build();
        ReflectionTestUtils.setField(profile, "id", 200L);
        when(guideProfileRepository.findByMemberId(100L)).thenReturn(Optional.of(profile));

        login("both@test.com", "ROLE_GUIDE");
        assertThat(support.resolveTourExtensionActorId()).isEqualTo(200L);
    }
}
