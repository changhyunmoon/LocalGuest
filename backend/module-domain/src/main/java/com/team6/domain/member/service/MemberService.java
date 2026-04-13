package com.team6.domain.member.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.SocialType;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public Long join(Member member) {
        //[LOG] INFO : [Member-Domain] 회원가입 로직 시작
        // 중복 회원 검증
        validateDuplicateMember(member);

        // 암호화된 비밀번호 값으로 교체
        // [LOG] DEBUG : [Member-Domain] 비밀번호 암호화 수행 중...
        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.updatePassword(encodedPassword);

        // 중복 아니면 DB저장
        memberRepository.save(member);
        //[LOG] INFO : [Member-Domain] 회원 저장 완료 (ID : {})
        return member.getId();
    }

    // 중복 회원 검증 로직
    private void validateDuplicateMember(Member member) {
        if(memberRepository.existsByEmail(member.getEmail())) {
            // [LOG] WARN : [Member-Domain] 중복 회원 가입 시도 감지
            throw new IllegalStateException("이미 존재하는 회원입니다. ");
        }
    }

    // 회원 탈퇴 기능 - common-module에 securityUtil구현 후 구현 가능
    @Transactional
    public void withdraw() {
        String email = SecurityUtil.getCurrentUserEmail();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다. "));
        if(member.getStatus() == Status.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴 처리된 회원입니다. ");
        }

        member.withdraw();
    }

    // 소셜로그인을 통한 위한 회원 조회 및 회원가입
    @Transactional
    public Member findOrCreateMember(String email, String name, String picture) {
        return memberRepository.findByEmail(email)
                .orElseGet(() -> {
                    String tempNickname = email.split("@")[0]
                            + "_" + (int)(Math.random()*10000);
                    // 조회 후 없으면 신규 소셜 회원 가입
                    Member newMember = Member.builder()
                            .email(email)
                            .name(name)
                            .password("")
                            .nickname(tempNickname)
                            .role(Role.GUEST)
                            .socialType(SocialType.GOOGLE)
                            .build();
                    return memberRepository.save(newMember);
                });
    }
}
