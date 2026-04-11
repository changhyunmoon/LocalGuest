package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideProfileAiCandidateMapperTest {

    @Test
    void toCandidate_should_extract_style_and_specialty_tags_from_guide_content() {
        GuideProfile profile = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("로컬가이드")
                .region("제주")
                .bio("감성 카페와 오션뷰 해변 포토스팟을 안내합니다.")
                .localStory("현지 골목과 전통시장을 함께 소개하는 로컬 투어를 운영합니다.")
                .language("한국어, English")
                .pricePerHour(new BigDecimal("70000"))
                .build();

        GuideFeed feed = GuideFeed.builder()
                .id(100L)
                .guideProfile(profile)
                .content("제주 바다 노을 맛집과 야시장 코스를 자주 소개합니다.")
                .build();

        GuideCareer career = GuideCareer.builder()
                .id(200L)
                .guideProfile(profile)
                .title("감성 사진 투어 운영")
                .description("카페, 야경, 인생샷 중심 코스를 운영한 경험")
                .acquiredAt(LocalDate.of(2024, 1, 1))
                .build();

        GuideRecommendRequest.GuideCandidateDto candidate =
                GuideProfileAiCandidateMapper.toCandidate(profile, List.of(feed), List.of(career));

        assertThat(candidate.getGuideStyle()).isEqualTo("감성");
        assertThat(candidate.getSpecialtyTags())
                .contains("카페", "바다", "사진", "시장", "맛집", "야경");
        assertThat(candidate.getLanguages()).containsExactly("한국어", "English");
        assertThat(candidate.getPriceLevel()).isEqualTo("중간");
    }
}
