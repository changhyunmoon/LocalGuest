package com.team6.domain.member.dto.response;

import com.team6.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberProfileResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImageUrl;

    public static MemberProfileResponse from(Member member) {
        return MemberProfileResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileImageUrl(member.getProfileImageUrl())
                .build();
    }
}