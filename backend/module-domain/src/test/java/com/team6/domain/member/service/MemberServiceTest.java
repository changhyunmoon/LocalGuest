package com.team6.domain.member.service;

import com.team6.domain.member.MemberApplicationTest;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = MemberApplicationTest.class)
@Transactional
public class MemberServiceTest {
    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;

    @Test
    void 중복_회원_예외_테스트() {
        Member m1 = Member.builder()
                .email("test@test.com")
                .name("user1")
                .nickname("userNickName1")
                .password("1234")
                .role(Role.GUIDE)
                .status(Status.ACTIVE)
                .build();
        Member m2 = Member.builder()
                .email("test@test.com")
                .name("user2")
                .nickname("userNickName2")
                .password("1234")
                .role(Role.GUIDE)
                .status(Status.ACTIVE)
                .build();

        memberService.join(m1);

        assertThrows(IllegalStateException.class, ()->{
            memberService.join(m2);
        });
    }
}
