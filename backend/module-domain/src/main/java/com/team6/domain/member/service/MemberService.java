package com.team6.domain.member.service;

import com.team6.domain.member.dto.request.*;
import com.team6.domain.member.dto.response.*;
import com.team6.domain.member.entity.*;
import com.team6.domain.member.repository.*;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final GuestProfileRepository guestProfileRepository;
    private final PasswordEncoder passwordEncoder;

    // ✅ 닉네임 중복 체크 (ACTIVE 상태만)
    public boolean existsByNickname(String nickname) {
        return memberRepository.existsByNicknameAndStatus(nickname, Status.ACTIVE);
    }

    // ✅ 회원가입 (완전히 새로 작성)
    @Transactional
    public Long join(MemberJoinRequest request) {
        // 1. 닉네임 중복 체크
        if (existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 2. 이메일 중복 체크 (기존 회원 조회)
        return memberRepository.findByEmail(request.getEmail())
                .map(existingMember -> {
                    // 2-1. 이미 가입된 이메일이 있는 경우
                    if (existingMember.getStatus() == Status.ACTIVE) {
                        throw new IllegalStateException("이미 가입된 계정이 존재합니다.");
                    }

                    // 2-2. 탈퇴 회원 재가입
                    String encodedPassword = passwordEncoder.encode(request.getPassword());
                    String finalNickname = request.getNickname();

                    // 닉네임 중복 시 랜덤 숫자 추가
                    if (existsByNickname(finalNickname)) {
                        finalNickname = finalNickname + "_" + (int)(Math.random() * 1000);
                    }

                    existingMember.reactivate(encodedPassword, request.getName(), finalNickname);

                    // 역할 설정
                    Role role = request.getRole() != null ? request.getRole() : Role.GUEST;
                    existingMember.addRole(role);

                    return existingMember.getId();
                })
                .orElseGet(() -> {
                    // 3. 신규 가입
                    String encodedPassword = passwordEncoder.encode(request.getPassword());

                    // Member 생성
                    Member member = Member.builder()
                            .email(request.getEmail())
                            .password(encodedPassword)
                            .name(request.getName())
                            .nickname(request.getNickname())
                            .status(Status.ACTIVE)
                            .build();

                    // 역할 설정 (기본 GUEST)
                    Role role = request.getRole() != null ? request.getRole() : Role.GUEST;
                    member.addRole(role);

                    Member savedMember = memberRepository.save(member);

                    // Guest 역할이면 Guest 프로필 생성
                    if (member.hasRole(Role.GUEST) && request.getGuestProfile() != null) {
                        GuestProfile guestProfile = request.getGuestProfile().toEntity(savedMember);
                        guestProfileRepository.save(guestProfile);
                        savedMember.setGuestProfile(guestProfile);
                    }

                    return savedMember.getId();
                });
    }

    // ✅ 회원 탈퇴 (수정)
    @Transactional
    public void withdraw() {
        String email = SecurityUtil.getCurrentUserEmail();

        // ✅ email로만 조회 (role 제거)
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        member.withdraw();
    }

    // ✅ 소셜로그인 회원 조회/생성 (수정)
    @Transactional
    public Member findOrCreateMember(String email, String name, String picture, Role selectedRole) {
        return memberRepository.findByEmail(email)
                .orElseGet(() -> {
                    // 닉네임 자동 생성
                    String tempNickname = email.split("@")[0] + "_" + (int)(Math.random()*10000);

                    // Member 생성
                    Member newMember = Member.builder()
                            .email(email)
                            .name(name)
                            .password("")  // 소셜 로그인은 비밀번호 없음
                            .nickname(tempNickname)
                            .socialType(SocialType.GOOGLE)
                            .status(Status.ACTIVE)
                            .build();

                    // 역할 추가
                    newMember.addRole(selectedRole);

                    Member savedMember = memberRepository.save(newMember);

                    // Guest 역할이면 기본 Guest 프로필 생성
                    if (selectedRole == Role.GUEST) {
                        GuestProfile guestProfile = GuestProfile.builder()
                                .member(savedMember)
                                .profileImageUrl(picture)  // 구글 프로필 사진
                                .build();
                        guestProfileRepository.save(guestProfile);
                        savedMember.setGuestProfile(guestProfile);
                    }

                    return savedMember;
                });
    }

    // ✅ Guest 프로필 조회
    public GuestProfileResponse getGuestProfile(Long memberId) {
        GuestProfile profile = guestProfileRepository.findByMemberId(memberId)
                .orElse(null);

        return GuestProfileResponse.from(profile);
    }

    // ✅ Guest 프로필 업데이트
    @Transactional
    public void updateGuestProfile(Long memberId, GuestProfileUpdateRequest request) {
        GuestProfile profile = guestProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Guest 프로필을 찾을 수 없습니다."));

        if (request.getProfileImageUrl() != null) {
            profile.updateProfileImage(request.getProfileImageUrl());
        }
        if (request.getBio() != null) {
            profile.updateBio(request.getBio());
        }
    }

    // ✅ 여행 선호도 업데이트
    @Transactional
    public void updateTravelPreference(Long memberId, TravelPreferenceUpdateRequest request) {
        GuestProfile guestProfile = guestProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Guest 프로필을 찾을 수 없습니다."));

        TravelPreference preference = guestProfile.getTravelPreference();

        if (preference == null) {
            throw new IllegalArgumentException("여행 선호도 정보를 찾을 수 없습니다.");
        }

        preference.update(
                request.getConcepts(),
                request.getPlanningStyle(),
                request.getCompanionType(),
                request.getPreferredDurationDays(),
                request.getDistancePreference(),
                request.getGuideMatchingStyle(),
                request.getInterestRegions()
        );
    }
}