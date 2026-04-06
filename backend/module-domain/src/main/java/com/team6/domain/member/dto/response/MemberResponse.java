package com.team6.domain.member.dto.response;

import com.team6.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberResponse {
    private Long id;
    private String email;
    private String nickname;

    public static MemberResponse from(Member member) {
        // [LOG] DEBUG : [Member-Domain] Entity -> Response 변환 (ID : {})
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .build();
    }
}
