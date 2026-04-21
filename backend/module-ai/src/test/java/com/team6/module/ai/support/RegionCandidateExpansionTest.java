package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCandidateExpansionTest {

    @Test
    void apply_should_use_exact_only_when_enough_guides_in_region() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                g(1L, "부산"),
                g(2L, "부산")
        );
        RegionCandidateExpansion.Result r = RegionCandidateExpansion.apply(pool, "부산");
        assertThat(r.candidates()).hasSize(2);
        assertThat(r.expansionUsed()).isFalse();
    }

    @Test
    void apply_should_treat_detailed_region_as_exact_match() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                g(1L, "제주특별자치도 제주시"),
                g(2L, "제주")
        );
        RegionCandidateExpansion.Result r = RegionCandidateExpansion.apply(pool, "제주");
        assertThat(r.candidates()).hasSize(2);
        assertThat(r.expansionUsed()).isFalse();
        assertThat(r.exactCount()).isEqualTo(2);
    }

    @Test
    void apply_should_expand_to_adjacent_when_exact_sparse() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                g(1L, "강릉"),
                g(2L, "속초")
        );
        RegionCandidateExpansion.Result r = RegionCandidateExpansion.apply(pool, "강릉");
        assertThat(r.candidates()).hasSize(2);
        assertThat(r.expansionUsed()).isTrue();
    }

    @Test
    void apply_should_expand_daegu_to_nearby_when_sparse() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                g(1L, "대구"),
                g(2L, "구미")
        );
        RegionCandidateExpansion.Result r = RegionCandidateExpansion.apply(pool, "대구");
        assertThat(r.candidates()).hasSize(2);
        assertThat(r.expansionUsed()).isTrue();
    }

    private static GuideRecommendRequest.GuideCandidateDto g(long id, String region) {
        return GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(id)
                .guideName("t")
                .region(region)
                .guideStyle("감성")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .build();
    }
}
