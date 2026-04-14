package com.team6.domain.guide.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 가이드 상세 통합 응답 DTO
 * 프로필 + 피드 + 경력 + 이미지를 단건으로 반환 (F06-01, 03)
 */
@Getter
@Builder
public class GuideDetailResponse {

    private GuideProfileResponse profile;       // 가이드 프로필
    private List<GuideFeedResponse> feeds;      // 피드 목록
    private List<GuideCareerResponse> careers;  // 경력 목록
    private List<GuideImageResponse> images;    // 이미지 목록
}
