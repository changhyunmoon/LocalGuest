package com.team6.module.ai.support;

/**
 * 룰 기반 추천의 공통 튜닝(스코어 가중·피드백 수치는 {@link com.team6.module.ai.config.ScoringPolicySettings}).
 * <p>
 * Diversity rerank는 {@link com.team6.module.ai.config.DiversityRerankSnapshot}·
 * {@code localguest.ai.diversity-rerank}(레거시 참고: {@link com.team6.module.ai.engine.DiversityRerankConstants}),
 * 정책별 점수 가중은 {@link com.team6.module.ai.config.ScoringPolicySnapshot}를 참고한다.
 * <p>
 * {@link #POLICY_VERSION}은 API 응답·Micrometer·DB 후보 풀 캐시 키 접두에 공통으로 쓰이므로,
 * 스코어·다양성·Reason 의미를 바꾸는 배포에서는 이 상수를 함께 올려 운영 지표와 후보 샘플링 결과가 어긋나지 않게 한다
 * ({@link com.team6.module.ai.config.LocalGuestAiProperties} 설명 참고).
 */
public final class AiRecommendationTuning {

    private AiRecommendationTuning() {
    }

    /**
     * 룰/스코어/Reason/응답 계약이 바뀔 때마다 올린다. 로그·API에서 동일 프롬프트 비교 시 정책 변경 여부를 구분하는 데 쓴다.
     */
    public static final String POLICY_VERSION = "2026.05.08";

    public static final int DEFAULT_TOP_N = 3;

    /** 추천 카드·응답에 실을 공개 피드 썸네일 URL 상한(최신순). */
    public static final int PUBLIC_FEED_THUMBNAIL_MAX = 4;

    /**
     * 요청 지역과 정확히 일치하는 가이드 수가 이 값 이상이면, 해당 지역 가이드만으로 후보를 제한한다.
     * 미만이면 {@link AdjacentRegionMap} 기반으로 인접 지역까지 후보를 넓힌다.
     */
    public static final int MIN_EXACT_REGION_CANDIDATES = 2;
}
