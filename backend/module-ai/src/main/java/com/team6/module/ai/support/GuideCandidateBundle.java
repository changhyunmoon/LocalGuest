package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.List;

/**
 * AI 추천에 사용할 후보 묶음.
 * <p>
 * - {@code candidates}: 일정/도메인 정책 등을 반영해 "메인 추천"에 사용할 후보\n+ * - {@code unfilteredCandidates}: 동일 풀에서 일정 필터만 제거했을 때의 후보(특별 제시 Top1 계산용)
 */
public record GuideCandidateBundle(
        List<GuideRecommendRequest.GuideCandidateDto> candidates,
        List<GuideRecommendRequest.GuideCandidateDto> unfilteredCandidates
) {
}

