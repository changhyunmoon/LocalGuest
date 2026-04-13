package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuideProfileAiCandidateMapperTest {

    @Test
    void toCandidate_should_extract_style_and_specialty_tags_from_guide_texts() {
        GuideProfile profile = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("제주감성가이드")
                .region("제주")
                .bio("감성 카페와 오션뷰 포토스팟 중심으로 안내합니다.")
                .localStory("현지 골목과 전통시장, 로컬 맛집을 함께 소개합니다.")
                .language("한국어, English")
                .pricePerHour(new BigDecimal("70000"))
                .build();

        GuideFeed feed = GuideFeed.builder()
                .id(100L)
                .guideProfile(profile)
                .content("노을이 예쁜 바다 카페, 야시장, 인생샷 스팟 코스를 운영합니다.")
                .build();

        GuideCareer career = GuideCareer.builder()
                .id(200L)
                .guideProfile(profile)
                .title("감성 사진 투어 운영")
                .description("카페, 사진, 맛집 중심 소규모 투어 경력")
                .acquiredAt(LocalDate.of(2024, 1, 1))
                .build();

        GuideRecommendRequest.GuideCandidateDto candidate =
                GuideProfileAiCandidateMapper.toCandidate(profile, List.of(feed), List.of(career));

        assertThat(candidate.getGuideStyle()).isEqualTo("감성");
        assertThat(candidate.getSpecialtyTags())
                .contains("카페", "바다", "사진", "시장", "맛집", "야경", "산책");
        assertThat(candidate.getLanguages()).containsExactly("한국어", "English");
        assertThat(candidate.getPriceLevel()).isEqualTo("중간");
    }

    @Test
    void toCandidate_should_map_profile_image_and_feed_thumbnails_newest_first() {
        GuideProfile profile = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("이미지가이드")
                .profileImage("https://cdn.example.com/profile/1.jpg")
                .region("제주")
                .language("한국어")
                .pricePerHour(new BigDecimal("40000"))
                .build();

        GuideFeed older = GuideFeed.builder()
                .id(1L)
                .guideProfile(profile)
                .content("옛날 피드")
                .imageUrl("https://cdn.example.com/feed/old.jpg")
                .build();
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2023, 6, 1, 12, 0));

        GuideFeed newer = GuideFeed.builder()
                .id(2L)
                .guideProfile(profile)
                .content("최근 피드")
                .imageUrl("https://cdn.example.com/feed/new.jpg")
                .build();
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2025, 1, 15, 9, 0));

        GuideFeed noImage = GuideFeed.builder()
                .id(3L)
                .guideProfile(profile)
                .content("이미지 없음")
                .build();
        ReflectionTestUtils.setField(noImage, "createdAt", LocalDateTime.of(2025, 2, 1, 9, 0));

        GuideRecommendRequest.GuideCandidateDto candidate = GuideProfileAiCandidateMapper.toCandidate(
                profile,
                List.of(older, newer, noImage),
                List.of()
        );

        assertThat(candidate.getRepresentativeImageUrl()).isEqualTo("https://cdn.example.com/profile/1.jpg");
        assertThat(candidate.getPublicFeedThumbnailUrls())
                .containsExactly("https://cdn.example.com/feed/new.jpg", "https://cdn.example.com/feed/old.jpg");
    }
}
