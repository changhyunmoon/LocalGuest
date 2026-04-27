package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 추천에 넣을 가이드 후보 목록을 결정한다.
 * <p>
 * 클라이언트가 {@code candidates}를 비우면, 구현체는 {@code prompt}(및 선택적 {@code topN})를 이용해
 * 서버 측에서 후보를 채울 수 있다(예: 상위 애플리케이션 모듈의 DB 조회 구현).
 * <p>
 * {@code desiredTourDate}가 있으면 해당 로컬 날짜 기준으로 도메인 정책(예: 결제 완료 스케줄)에 따라
 * 후보를 제외할 수 있다(구현체가 DB를 쓰는 경우).
 */
public interface GuideCandidateProvider {
    GuideCandidateBundle getCandidates(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            LocalDate desiredTourDateFrom,
            LocalDate desiredTourDateTo
    );
}