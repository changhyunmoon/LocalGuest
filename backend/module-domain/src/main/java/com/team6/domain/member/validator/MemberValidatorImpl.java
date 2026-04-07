package com.team6.domain.member.validator;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.excpetion.MemberNotFoundException;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.entity.MemberValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MemberValidatorImpl implements MemberValidator {

    private final MemberRepository memberRepository;

    @Override
    public void validateMemberExist(List<Long> memberIds){
        List<Member> foundMembers = memberRepository.findAllById(memberIds);

        if(foundMembers.size() != memberIds.size()){
//            //어떤 id가 없는지 확인
//            List<Long> foundIds = foundMembers.stream()
//                    .map(Member::getId)
//                    .toList();
//            List<Long> notFoundIds = memberIds.stream()
//                    .filter(id -> !foundIds.contains(id))
//                    .toList();

            throw new MemberNotFoundException();
        }
    }

    @Override
    public String getNickname(Long userId) {
        return memberRepository.findById(userId)
                .map(Member::getNickname)
                .orElseThrow(MemberNotFoundException::new);
    }

    // 검증 + 닉네임 한 번의 쿼리로 해결
    @Override
    public Map<Long, String> getValidatedNicknameMap(List<Long> memberIds) {
        List<Member> foundMembers = memberRepository.findAllById(memberIds);

        if (foundMembers.size() != memberIds.size()) {
//            List<Long> foundIds = foundMembers.stream()
//                    .map(Member::getId)
//                    .toList();
//
//            List<Long> notFoundIds = memberIds.stream()
//                    .filter(id -> !foundIds.contains(id))
//                    .toList();

            throw new MemberNotFoundException();
        }

        return foundMembers.stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
    }


}
