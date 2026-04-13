package com.team6.module.ai.policy;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AdjacentRegionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionMatchPolicyTest {

    @Test
    void score_exact_region_full_weight() {
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(new LocalGuestAiProperties());
        RegionMatchPolicy policy = new RegionMatchPolicy(adjacent);
        TravelerPreference pref = TravelerPreference.builder().region("부산").build();
        GuideAiProfile guide = GuideAiProfile.builder().region("부산").build();
        assertThat(policy.score(pref, guide)).isEqualTo(ScoreWeight.REGION);
    }

    @Test
    void score_adjacent_region_partial_weight() {
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(new LocalGuestAiProperties());
        RegionMatchPolicy policy = new RegionMatchPolicy(adjacent);
        TravelerPreference pref = TravelerPreference.builder().region("강릉").build();
        GuideAiProfile guide = GuideAiProfile.builder().region("속초").build();
        assertThat(policy.score(pref, guide)).isEqualTo(ScoreWeight.REGION_ADJACENT);
    }

    @Test
    void score_non_adjacent_zero() {
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(new LocalGuestAiProperties());
        RegionMatchPolicy policy = new RegionMatchPolicy(adjacent);
        TravelerPreference pref = TravelerPreference.builder().region("제주").build();
        GuideAiProfile guide = GuideAiProfile.builder().region("부산").build();
        assertThat(policy.score(pref, guide)).isZero();
    }

    @Test
    void score_yaml_adjacent_overrides_builtin() {
        LocalGuestAiProperties props = new LocalGuestAiProperties();
        props.getAdjacentRegions().put("테스트시", List.of("테스트동"));
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(props);
        RegionMatchPolicy policy = new RegionMatchPolicy(adjacent);
        TravelerPreference pref = TravelerPreference.builder().region("테스트시").build();
        GuideAiProfile guide = GuideAiProfile.builder().region("테스트동").build();
        assertThat(policy.score(pref, guide)).isEqualTo(ScoreWeight.REGION_ADJACENT);
    }
}
