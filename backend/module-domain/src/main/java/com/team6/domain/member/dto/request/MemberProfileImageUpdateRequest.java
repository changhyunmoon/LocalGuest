package com.team6.domain.member.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberProfileImageUpdateRequest {

    /**
     * null = 변경 없음 / 빈 문자열 = 삭제 로 두고 싶으면 팀 규칙에 맞게 조정
     * 여기서는 "nullable String: null=유지, blank=삭제" 추천
     */
    @Size(max = 1000, message = "프로필 이미지 URL이 너무 깁니다.")
    private String profileImageUrl;
}