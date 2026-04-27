package com.team6.module.ai.llm;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmGuideRecommendMapperTest {

    @Test
    void toRequest_truncates_specialRequests_over500() {
        String longText = "가".repeat(520);
        LlmGuideRecommendJson j = new LlmGuideRecommendJson();
        j.setRegion("제주");
        j.setSpecialRequests(longText);

        GuideRecommendRequest r = LlmGuideRecommendMapper.toRequest(j, 3, List.of());

        assertThat(r.getLlmSpecialRequests()).hasSize(500);
    }
}
