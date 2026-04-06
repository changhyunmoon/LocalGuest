package com.team6.domain.member.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;

    // 회원가입
    @Transactional
    public Long join(Member member) {
        //[LOG] INFO : [Member-Domain] 회원가입 로직 시작
        // 중복 회원 검증
        validateDuplicateMeber(member);
        // 중복 아니면 DB저장
        memberRepository.save(member);
        //[LOG] INFO : [Member-Domain] 회원 저장 완료 (ID : {})
        return member.getId();
    }

    // 중복 회원 검증 로직
    private void validateDuplicateMeber(Member member) {
        if(memberRepository.existsByEmail(member.getEmail())) {
            // [LOG] WARN : [Member-Domain] 중복 회원 가입 시도 감지
            throw new IllegalStateException("이미 존재하는 회원입니다. ");
        }
    }
}
