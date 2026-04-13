package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.repository.GuideCareerRepository;
import com.team6.domain.guide.repository.GuideFeedRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.RefundRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.GuideCandidateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 클라이언트가 후보를 내려보내지 않은 경우, 승인·활성 {@link GuideProfile}을 DB에서 읽어 AI 후보로 채운다.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DbBackedGuideCandidateProvider implements GuideCandidateProvider {

    static final int MAX_SERVER_CANDIDATES = 200;
    private static final List<MatchRequestStatus> PROGRESSED_MATCH_STATUSES = List.of(
            MatchRequestStatus.ACCEPTED,
            MatchRequestStatus.PAID,
            MatchRequestStatus.IN_PROGRESS,
            MatchRequestStatus.COMPLETED
    );

    private final GuideProfileRepository guideProfileRepository;
    private final GuideFeedRepository guideFeedRepository;
    private final GuideCareerRepository guideCareerRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final RefundRepository refundRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PromptParser promptParser;

    @Override
    public List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> candidates
    ) {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }
        String safePrompt = prompt == null ? "" : prompt;
        int resolvedTopN = (topN == null || topN <= 0) ? AiRecommendationTuning.DEFAULT_TOP_N : topN;
        GuideRecommendRequest parsed = promptParser.parse(safePrompt, resolvedTopN, List.of());
        String region = parsed.getRegion();

        Pageable pageable = PageRequest.of(0, MAX_SERVER_CANDIDATES);
        List<GuideProfile> profiles;
        if (region != null && !region.isBlank()) {
            String fragment = region.trim();
            profiles = guideProfileRepository
                    .findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(fragment, pageable)
                    .getContent();
            if (profiles.isEmpty()) {
                log.info("[AI_RECOMMEND] No approved-active guides for region fragment={}, using global pool", fragment);
                profiles = guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(pageable).getContent();
            }
        } else {
            log.info("[AI_RECOMMEND] No region in prompt; loading global approved-active guide pool");
            profiles = guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(pageable).getContent();
        }
        List<GuideRecommendRequest.GuideCandidateDto> mapped =
                mapProfilesToCandidates(profiles);
        return mergeBehaviorSignals(mapped, profiles);
    }

    private List<GuideRecommendRequest.GuideCandidateDto> mapProfilesToCandidates(List<GuideProfile> profiles) {
        List<Long> guideIds = profiles.stream()
                .map(GuideProfile::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<GuideFeed>> feedsByGuide = guideIds.isEmpty()
                ? Map.of()
                : guideFeedRepository.findByGuideProfile_IdInAndIsDeletedFalse(guideIds).stream()
                .filter(feed -> feed.getGuideProfile() != null && feed.getGuideProfile().getId() != null)
                .collect(Collectors.groupingBy(
                        feed -> feed.getGuideProfile().getId(),
                        Collectors.toList()
                ));

        Map<Long, List<GuideCareer>> careersByGuide = guideIds.isEmpty()
                ? Map.of()
                : guideCareerRepository.findByGuideProfile_IdIn(guideIds).stream()
                .filter(career -> career.getGuideProfile() != null && career.getGuideProfile().getId() != null)
                .collect(Collectors.groupingBy(
                        career -> career.getGuideProfile().getId(),
                        Collectors.toList()
                ));

        return profiles.stream()
                .map(profile -> GuideProfileAiCandidateMapper.toCandidate(
                        profile,
                        feedsByGuide.getOrDefault(profile.getId(), List.of()),
                        careersByGuide.getOrDefault(profile.getId(), List.of())
                ))
                .toList();
    }

    private List<GuideRecommendRequest.GuideCandidateDto> mergeBehaviorSignals(
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            List<GuideProfile> profiles
    ) {
        List<Long> guideIds = candidates.stream()
                .map(GuideRecommendRequest.GuideCandidateDto::getGuideId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (guideIds.isEmpty()) {
            return candidates;
        }
        List<Object[]> rows = refundRepository.countApprovedRefundsGroupedByGuideId(guideIds, RefundStatus.APPROVED);
        Map<Long, Integer> countByGuide = new HashMap<>();
        for (Object[] row : rows) {
            Long gid = (Long) row[0];
            int cnt = ((Number) row[1]).intValue();
            countByGuide.put(gid, cnt);
        }

        Map<Long, Integer> requestCountByGuide = toCountMap(matchRequestRepository.countAllGroupedByGuideId(guideIds));
        Map<Long, Integer> progressedCountByGuide = toCountMap(
                matchRequestRepository.countByGuideIdAndStatusInGrouped(guideIds, PROGRESSED_MATCH_STATUSES)
        );
        Map<Long, Long> memberIdByGuide = profiles.stream()
                .filter(profile -> profile.getId() != null && profile.getMemberId() != null)
                .collect(Collectors.toMap(GuideProfile::getId, GuideProfile::getMemberId));
        List<Long> memberIds = memberIdByGuide.values().stream().distinct().toList();
        Map<Long, Integer> chatCountByMember = memberIds.isEmpty()
                ? Map.of()
                : toCountMap(chatRoomRepository.countRoomsGroupedByParticipantUserId(memberIds));

        return candidates.stream()
                .map(d -> rebuildWithSignals(
                        d,
                        countByGuide.getOrDefault(d.getGuideId(), 0),
                        requestCountByGuide.getOrDefault(d.getGuideId(), 0),
                        progressedCountByGuide.getOrDefault(d.getGuideId(), 0),
                        chatCountByMember.getOrDefault(memberIdByGuide.get(d.getGuideId()), 0)
                ))
                .toList();
    }

    private static Map<Long, Integer> toCountMap(List<Object[]> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            int cnt = ((Number) row[1]).intValue();
            counts.put(id, cnt);
        }
        return counts;
    }

    private static GuideRecommendRequest.GuideCandidateDto rebuildWithSignals(
            GuideRecommendRequest.GuideCandidateDto d,
            int approvedRefundCount,
            int matchRequestCount,
            int progressedMatchCount,
            int chatStartCount
    ) {
        return GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(d.getGuideId())
                .guideName(d.getGuideName())
                .region(d.getRegion())
                .guideStyle(d.getGuideStyle())
                .priceLevel(d.getPriceLevel())
                .specialtyTags(d.getSpecialtyTags())
                .languages(d.getLanguages())
                .averageRating(d.getAverageRating())
                .reviewCount(d.getReviewCount())
                .approvedRefundCount(approvedRefundCount)
                .matchRequestCount(matchRequestCount)
                .progressedMatchCount(progressedMatchCount)
                .chatStartCount(chatStartCount)
                .representativeImageUrl(d.getRepresentativeImageUrl())
                .publicFeedThumbnailUrls(d.getPublicFeedThumbnailUrls())
                .build();
    }
}
