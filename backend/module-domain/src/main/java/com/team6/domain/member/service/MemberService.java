package com.team6.domain.member.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.SocialType;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public Long join(Member member) {
        // log.info("[Member-Domain] 회원가입 로직 시작 - email: {}", member.getEmail());

        // 암호화된 비밀번호 값으로 교체
        // [LOG] DEBUG : [Member-Domain] 비밀번호 암호화 수행 중...
        String encodedPassword = passwordEncoder.encode(member.getPassword());

        return memberRepository.findByEmailAndRole(member.getEmail(), member.getRole())
                .map(existingMember -> {
                    // 이메일 + role 조합으로 기존 가입 이력 확인
                    if(existingMember.getStatus() == Status.ACTIVE) {
                        throw new IllegalStateException("이미 해당 역할로 가입된 계정이 존재합니다. ");
                    }

                    // 탈퇴 회원 재가입 로직
                    String finalNickname = member.getNickname();
                    if(memberRepository.existsByNickname(finalNickname)) {
                        finalNickname = finalNickname + "_" + (int)(Math.random() * 1000);
                    }

                    existingMember.reactivate(encodedPassword, member.getName(), finalNickname);
                    return existingMember.getId();
                })
                .orElseGet(()->{
                    // 신규 가입
                    if (memberRepository.existsByNicknameAndStatus(member.getNickname(), Status.ACTIVE)) {
                        throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
                    }

                    member.updatePassword(encodedPassword);
                    return memberRepository.save(member).getId();
                });
    }

    // 회원 탈퇴 기능 - common-module에 securityUtil구현 후 구현 가능
    @Transactional
    public void withdraw(Role role) {
        String email = SecurityUtil.getCurrentUserEmail();
        Member member = memberRepository.findByEmailAndRole(email, role)
                .orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다. "));

        member.withdraw();
    }

    // 소셜로그인을 통한 위한 회원 조회 및 회원가입
    @Transactional
    public Member findOrCreateMember(String email, String name, String picture, Role selectedRole) {
        return memberRepository.findByEmailAndRole(email, selectedRole)
                .orElseGet(() -> {
                    String tempNickname = email.split("@")[0]
                            + "_" + (int)(Math.random()*10000);
                    // 조회 후 없으면 신규 소셜 회원 가입
                    Member newMember = Member.builder()
                            .email(email)
                            .name(name)
                            .password("")
                            .nickname(tempNickname)
                            .role(selectedRole)
                            .socialType(SocialType.GOOGLE)
                            .status(Status.ACTIVE)
                            .build();
                    return memberRepository.save(newMember);
                });
    }

    // 닉네임 중복 체크
    public boolean existsByNickname(String nickname) {
        return memberRepository.existsByNicknameAndStatus(nickname, Status.ACTIVE);
    }

    // 현재 로그인한 사용자 조회
    @Transactional(readOnly = true)
    public Member getCurrentMember() {
        String email = SecurityUtil.getCurrentUserEmail();
        String roleString = SecurityUtil.getCurrentUserRoleString();
        Role role = Role.valueOf(roleString.replace("ROLE_", ""));
        return memberRepository.findByEmailAndRole(email, role)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. "));
    }


    // 게스트 전용 API 호출 시 검증
    private void requireGuest() {
        String roleString = SecurityUtil.getCurrentUserRoleString();
        Role role = Role.valueOf(roleString.replace("ROLE_", ""));
        if (role != Role.GUEST) {
            throw new IllegalStateException("게스트 전용 API입니다.");
        }
    }

    // 프로필 이미지 업데이트
    @Transactional
    public void updateMyProfileImage(String urlOrNull, boolean clearWhenBlank) {
        requireGuest();
        
        Member member = getCurrentMember();
        if (urlOrNull == null) {
            return;
        }
        if (urlOrNull.isBlank() && clearWhenBlank) {
            member.updateProfileImage(null);
            return;
        }
        if (!urlOrNull.isBlank()) {
            member.updateProfileImage(urlOrNull);
        }
    }
}


