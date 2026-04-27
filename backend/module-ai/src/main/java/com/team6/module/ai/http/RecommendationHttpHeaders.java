package com.team6.module.ai.http;

/**
 * AI 추천 API 응답 헤더 이름. 본문 {@code policyVersion}과 동일한 값을 실을 때 사용한다.
 */
public final class RecommendationHttpHeaders {

    private RecommendationHttpHeaders() {
    }

    public static final String X_RECOMMENDATION_POLICY = "X-Recommendation-Policy";
}
