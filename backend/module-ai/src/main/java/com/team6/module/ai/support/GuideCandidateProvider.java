package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.List;

/**
 * AI 추천에 넣을 가이드 후보 목록을 결정한다.
 * <p>
 * 클라이언트가 {@code candidates}를 비우면, 구현체는 {@code prompt}(및 선택적 {@code topN})를 이용해
 * 서버 측에서 후보를 채울 수 있다(예: 상위 애플리케이션 모듈의 DB 조회 구현).
 */
public interface GuideCandidateProvider {
    List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> candidates
    );
}