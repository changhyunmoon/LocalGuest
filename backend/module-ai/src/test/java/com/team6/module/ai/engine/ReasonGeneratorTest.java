package com.team6.module.ai.engine;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AdjacentRegionProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonGeneratorTest {

    private static ReasonGenerator generator() {
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        return new ReasonGenerator(new AdjacentRegionProvider(new LocalGuestAiProperties()), scoring);
    }

    @Test
    void generate_should_use_budget_adjacent_code() {
        TravelerPreference pref = TravelerPreference.builder()
                .region("서울")
                .budgetLevel("낮음")
                .build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .region("서울")
                .priceLevel("중간")
                .build();

        ReasonBundle bundle = generator().generate(pref, guide, 0);

        assertThat(bundle.getReasonCodes()).contains(ReasonGenerator.CODE_BUDGET_ADJACENT);
        assertThat(bundle.getText()).contains("예산");
    }

    @Test
    void generate_should_split_very_low_rating_code() {
        TravelerPreference pref = TravelerPreference.builder().region("제주").build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .region("제주")
                .averageRating(BigDecimal.valueOf(2.0))
                .reviewCount(ScoringPolicySnapshot.defaults().feedbackLowRatingMinReviews())
                .build();

        ReasonBundle bundle = generator().generate(pref, guide, 0);

        assertThat(bundle.getReasonCodes()).contains(ReasonGenerator.CODE_FEEDBACK_VERY_LOW_RATING);
        assertThat(bundle.getReasonCodes()).doesNotContain(ReasonGenerator.CODE_FEEDBACK_LOW_RATING);
        assertThat(bundle.getText()).contains("평균 리뷰");
    }

    @Test
    void generate_should_include_region_adjacent_copy() {
        TravelerPreference pref = TravelerPreference.builder().region("강릉").build();
        GuideAiProfile guide = GuideAiProfile.builder().region("속초").build();

        ReasonBundle bundle = generator().generate(pref, guide, 0);

        assertThat(bundle.getReasonCodes()).contains(ReasonGenerator.CODE_REGION_ADJACENT);
        assertThat(bundle.getText()).contains("인접");
    }

    @Test
    void generate_should_describe_activity_match() {
        TravelerPreference pref = TravelerPreference.builder()
                .region("부산")
                .activityTags(List.of("카페"))
                .build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .region("부산")
                .specialtyTags(List.of("카페"))
                .build();

        ReasonBundle bundle = generator().generate(pref, guide, 0);

        assertThat(bundle.getReasonCodes()).contains(ReasonGenerator.CODE_ACTIVITY_MATCH);
        assertThat(bundle.getText()).contains("관심 활동");
    }
}
