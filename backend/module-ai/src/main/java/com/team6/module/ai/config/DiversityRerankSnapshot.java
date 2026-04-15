package com.team6.module.ai.config;

import com.team6.module.ai.engine.DiversityRerankConstants;

/**
 * Top-N 다양성 패널티에 쓰는 스냅샷({@code localguest.ai.diversity-rerank}).
 * <p>
 * 유사도는 대략 0~1로 정규화한 뒤 {@code diversityLambda}를 곱해 점수에서 뺀다.
 * 가중치 합으로 나누어 스케일을 맞춘다.
 */
public record DiversityRerankSnapshot(
        double diversityLambda,
        double regionSimWeight,
        double regionClusterSimilarityRatio,
        double styleSimWeight,
        double tagSimWeight,
        double languageSimWeight,
        double priceTierSimWeight,
        /**
         * 예산 티어가 정확히 같지 않지만 {@link com.team6.module.ai.support.BudgetTier#adjacentTiers}일 때의 부분 유사도(0~1).
         */
        double priceAdjacentSimilarityRatio,
        /**
         * 전문 태그 Jaccard가 이 값 이상이면 {@link #tagNearDuplicateBoost}로 유효 Jaccard를 올려 유사 코스 과다 노출을 억제한다.
         */
        double tagNearDuplicateThreshold,
        double tagNearDuplicateBoost
) {
    public static DiversityRerankSnapshot from(DiversityRerankSettings s) {
        if (s == null) {
            return defaults();
        }
        return new DiversityRerankSnapshot(
                s.getDiversityLambda(),
                s.getRegionSimWeight(),
                s.getRegionClusterSimilarityRatio(),
                s.getStyleSimWeight(),
                s.getTagSimWeight(),
                s.getLanguageSimWeight(),
                s.getPriceTierSimWeight(),
                s.getPriceAdjacentSimilarityRatio(),
                s.getTagNearDuplicateThreshold(),
                s.getTagNearDuplicateBoost()
        );
    }

    public static DiversityRerankSnapshot defaults() {
        return new DiversityRerankSnapshot(
                DiversityRerankConstants.DIVERSITY_LAMBDA,
                DiversityRerankConstants.REGION_SIM_WEIGHT,
                DiversityRerankConstants.REGION_CLUSTER_SIMILARITY_RATIO,
                DiversityRerankConstants.STYLE_SIM_WEIGHT,
                DiversityRerankConstants.TAG_SIM_WEIGHT,
                DiversityRerankConstants.LANGUAGE_SIM_WEIGHT,
                DiversityRerankConstants.PRICE_TIER_SIM_WEIGHT,
                DiversityRerankConstants.DEFAULT_PRICE_ADJACENT_SIMILARITY_RATIO,
                DiversityRerankConstants.DEFAULT_TAG_NEAR_DUP_THRESHOLD,
                DiversityRerankConstants.DEFAULT_TAG_NEAR_DUP_BOOST
        );
    }
}
