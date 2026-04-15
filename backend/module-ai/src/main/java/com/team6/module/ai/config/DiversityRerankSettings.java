package com.team6.module.ai.config;

import com.team6.module.ai.engine.DiversityRerankConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * YAML 바인딩: {@code localguest.ai.diversity-rerank}.
 * <p>
 * 후보 풀({@link LocalGuestAiProperties#getCandidatePool()})과는 별도 축이나,
 * 같은 배포에서 함께 조정하는 것을 권장한다. 랭킹 의미 변경 시 {@link com.team6.module.ai.support.AiRecommendationTuning#POLICY_VERSION} 상향을 검토한다.
 */
@Getter
@Setter
public class DiversityRerankSettings {

    private double diversityLambda = DiversityRerankConstants.DIVERSITY_LAMBDA;
    private double regionSimWeight = DiversityRerankConstants.REGION_SIM_WEIGHT;
    private double regionClusterSimilarityRatio = DiversityRerankConstants.REGION_CLUSTER_SIMILARITY_RATIO;
    private double styleSimWeight = DiversityRerankConstants.STYLE_SIM_WEIGHT;
    private double tagSimWeight = DiversityRerankConstants.TAG_SIM_WEIGHT;
    private double languageSimWeight = DiversityRerankConstants.LANGUAGE_SIM_WEIGHT;
    private double priceTierSimWeight = DiversityRerankConstants.PRICE_TIER_SIM_WEIGHT;
    private double priceAdjacentSimilarityRatio = DiversityRerankConstants.DEFAULT_PRICE_ADJACENT_SIMILARITY_RATIO;
    private double tagNearDuplicateThreshold = DiversityRerankConstants.DEFAULT_TAG_NEAR_DUP_THRESHOLD;
    private double tagNearDuplicateBoost = DiversityRerankConstants.DEFAULT_TAG_NEAR_DUP_BOOST;
}
