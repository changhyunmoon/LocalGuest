package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.repository.GuideCareerRepository;
import com.team6.domain.guide.repository.GuideFeedRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.RefundRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.GuideCandidateBundle;
import com.team6.module.ai.support.GuideCandidateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 클라이언트가 후보를 내려보내지 않은 경우, 승인·활성 {@link GuideProfile}을 DB에서 읽어 AI 후보로 채운다.
 * <p>
 * 지역은 <b>정확 일치 → 부분 일치 → 전역</b> 순으로 확보하고, 운영 제외 ID·상한·콜드스타트 비율 샘플링·
 * 정책버전 키 캐시({@link LocalGuestAiProperties#getCandidatePool()})를 적용한다.
 * <p>
 * <b>캐시 키</b>: {@link AiRecommendationTuning#POLICY_VERSION}을 접두로 하고,
 * {@code maxFetchSize}·{@code maxPoolSize}·{@code coldStartReserveRatio}·{@code excludedGuideIds}·지역 문자열을
 * 이어 붙인다. 스코어/다양성 YAML 내용 전체는 키에 넣지 않으므로, 랭킹 의미가 바뀌면
 * {@code POLICY_VERSION}을 올려 캐시를 자연스럽게 무효화하고, API {@code policyVersion}·메트릭 태그와 맞춘다.
 * 후보 풀 차원만 바꿀 때는 위 후보 풀 필드가 키에 들어가므로 별도 버전 상향 없이도 다른 엔트리가 된다.
 * <p>
 * <b>결제 완료 일정 제외</b>: 희망 기간({@code desiredTourDateFrom~desiredTourDateTo})가 지정되면, 기간 중 하루라도
 * {@link GuideScheduleStatus#BOOKED}이면서 {@code isPaid=true}인 스케줄이 있는 가이드는 후보에서 제외한다.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DbBackedGuideCandidateProvider implements GuideCandidateProvider {

    /** {@link LocalGuestAiProperties.CandidatePoolSettings#getMaxFetchSize()} 기본값과 동기화 */
    public static final int DEFAULT_MAX_FETCH_SIZE = 200;

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
    private final GuideScheduleRepository guideScheduleRepository;
    private final PromptParser promptParser;
    private final LocalGuestAiProperties aiProperties;
    private final AiRecommendationMetrics metrics;

    private final ConcurrentHashMap<String, PoolCacheEntry> poolCache = new ConcurrentHashMap<>();

    @Override
    public GuideCandidateBundle getCandidates(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            LocalDate desiredTourDateFrom,
            LocalDate desiredTourDateTo
    ) {
        if (candidates != null && !candidates.isEmpty()) {
            List<GuideRecommendRequest.GuideCandidateDto> unfiltered = candidates;
            Set<Long> bookedPaidGuideIds = resolveBookedPaidGuideIds(desiredTourDateFrom, desiredTourDateTo);
            List<GuideRecommendRequest.GuideCandidateDto> filtered =
                    excludeBookedPaidGuides(unfiltered, bookedPaidGuideIds, desiredTourDateFrom, desiredTourDateTo);
            metrics.recordCandidateExclusion(
                    "schedule_conflict",
                    unfiltered.size() - filtered.size(),
                    AiRecommendationTuning.POLICY_VERSION
            );
            return new GuideCandidateBundle(filtered, unfiltered);
        }
        String safePrompt = prompt == null ? "" : prompt;
        int resolvedTopN = (topN == null || topN <= 0) ? AiRecommendationTuning.DEFAULT_TOP_N : topN;
        GuideRecommendRequest parsed = promptParser.parse(safePrompt, resolvedTopN, List.of());
        String region = parsed.getRegion();

        LocalGuestAiProperties.CandidatePoolSettings pool = aiProperties.getCandidatePool();
        int ttlSec = pool.getPoolCacheTtlSeconds();
        String cacheKey = buildPoolCacheKey(region, pool);
        if (ttlSec > 0) {
            PoolCacheEntry hit = poolCache.get(cacheKey);
            if (hit != null && !hit.isExpired()) {
                log.debug("[AI_POOL] cache hit key={}", cacheKey);
                List<GuideRecommendRequest.GuideCandidateDto> unfiltered = List.copyOf(hit.candidates());
                Set<Long> bookedPaidGuideIds = resolveBookedPaidGuideIds(desiredTourDateFrom, desiredTourDateTo);
                List<GuideRecommendRequest.GuideCandidateDto> filtered =
                        excludeBookedPaidGuides(unfiltered, bookedPaidGuideIds, desiredTourDateFrom, desiredTourDateTo);
                return new GuideCandidateBundle(filtered, unfiltered);
            }
        }

        List<GuideProfile> profiles = loadProfilesTiered(region, pool);
        int beforeExcluded = profiles.size();
        profiles = filterExcludedProfiles(profiles, pool);
        metrics.recordCandidateExclusion(
                "config_excluded",
                beforeExcluded - profiles.size(),
                AiRecommendationTuning.POLICY_VERSION
        );

        List<GuideRecommendRequest.GuideCandidateDto> mapped = mapProfilesToCandidates(profiles);
        List<GuideRecommendRequest.GuideCandidateDto> withSignals = mergeBehaviorSignals(mapped, profiles);
        List<GuideRecommendRequest.GuideCandidateDto> sampledUnfiltered = applyPoolSampling(withSignals, pool);
        Set<Long> bookedPaidGuideIds = resolveBookedPaidGuideIds(desiredTourDateFrom, desiredTourDateTo);
        List<GuideRecommendRequest.GuideCandidateDto> sampledFiltered =
                excludeBookedPaidGuides(sampledUnfiltered, bookedPaidGuideIds, desiredTourDateFrom, desiredTourDateTo);
        metrics.recordCandidateExclusion(
                "schedule_conflict",
                sampledUnfiltered.size() - sampledFiltered.size(),
                AiRecommendationTuning.POLICY_VERSION
        );

        if (ttlSec > 0) {
            long ttlNanos = ttlSec * 1_000_000_000L;
            long expiresAt = System.nanoTime() + ttlNanos;
            poolCache.put(cacheKey, new PoolCacheEntry(sampledUnfiltered, expiresAt));
            log.debug("[AI_POOL] cache put key={} size={}", cacheKey, sampledUnfiltered.size());
        }

        return new GuideCandidateBundle(sampledFiltered, sampledUnfiltered);
    }

    private Set<Long> resolveBookedPaidGuideIds(LocalDate desiredFrom, LocalDate desiredTo) {
        if (desiredFrom == null) {
            return Set.of();
        }
        LocalDate to = desiredTo == null ? desiredFrom : desiredTo;
        LocalDate from = desiredFrom.isAfter(to) ? to : desiredFrom;
        List<Long> ids = guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(
                from,
                to,
                GuideScheduleStatus.BOOKED
        );
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
    }

    private static List<GuideRecommendRequest.GuideCandidateDto> excludeBookedPaidGuides(
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            Set<Long> bookedPaidGuideIds,
            LocalDate desiredFrom,
            LocalDate desiredTo
    ) {
        if (candidates == null) {
            return List.of();
        }
        if (bookedPaidGuideIds.isEmpty()) {
            return candidates;
        }
        int before = candidates.size();
        List<GuideRecommendRequest.GuideCandidateDto> out = candidates.stream()
                .filter(c -> c.getGuideId() == null || !bookedPaidGuideIds.contains(c.getGuideId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (out.size() < before) {
            log.info("[AI_POOL] bookedPaidExclusion candidates from={} to={} removed={}",
                    desiredFrom, desiredTo, before - out.size());
        }
        return out;
    }

    /**
     * 스코어·다양성 스냅샷 필드는 포함하지 않는다(후보 집합 차원만 반영).
     */
    private static String buildPoolCacheKey(
            String region,
            LocalGuestAiProperties.CandidatePoolSettings pool
    ) {
        String regionKey = region == null || region.isBlank() ? "_" : region.trim().toLowerCase(Locale.ROOT);
        return AiRecommendationTuning.POLICY_VERSION
                + "|" + pool.getMaxFetchSize()
                + "|" + pool.getMaxPoolSize()
                + "|" + pool.getColdStartReserveRatio()
                + "|" + pool.getExcludedGuideIds()
                + "|" + regionKey;
    }

    private List<GuideProfile> loadProfilesTiered(String region, LocalGuestAiProperties.CandidatePoolSettings pool) {
        int maxFetch = Math.max(1, pool.getMaxFetchSize());
        Pageable pageable = PageRequest.of(0, maxFetch, Sort.by(Sort.Direction.DESC, "id"));

        if (region == null || region.isBlank()) {
            log.info("[AI_POOL] no region in prompt; loading global approved-active pool (maxFetch={})", maxFetch);
            return guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(pageable).getContent();
        }

        String fragment = region.trim();
        List<GuideProfile> tiered = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();

        Page<GuideProfile> exact = guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionEqualsIgnoreCase(
                fragment,
                pageable
        );
        addUnique(tiered, seen, exact.getContent());

        if (tiered.size() < maxFetch) {
            Pageable partialPage = PageRequest.of(0, maxFetch - tiered.size(), Sort.by(Sort.Direction.DESC, "id"));
            Page<GuideProfile> partial = guideProfileRepository
                    .findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(fragment, partialPage);
            addUnique(tiered, seen, partial.getContent());
        }

        if (tiered.isEmpty()) {
            log.info("[AI_POOL] no approved-active guides for region fragment={}, using global pool", fragment);
            tiered.addAll(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(pageable).getContent());
        } else {
            log.debug("[AI_POOL] region={} tiered size={} (exact+partial)", fragment, tiered.size());
        }

        return tiered;
    }

    private static void addUnique(List<GuideProfile> out, Set<Long> seen, List<GuideProfile> chunk) {
        for (GuideProfile p : chunk) {
            if (p.getId() == null || seen.contains(p.getId())) {
                continue;
            }
            seen.add(p.getId());
            out.add(p);
        }
    }

    private static List<GuideProfile> filterExcludedProfiles(
            List<GuideProfile> profiles,
            LocalGuestAiProperties.CandidatePoolSettings pool
    ) {
        List<Long> excluded = pool.getExcludedGuideIds();
        if (excluded == null || excluded.isEmpty()) {
            return profiles;
        }
        Set<Long> block = excluded.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (block.isEmpty()) {
            return profiles;
        }
        return profiles.stream()
                .filter(p -> p.getId() == null || !block.contains(p.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 콜드스타트 후보를 일정 비율 확보한 뒤 무작위 샘플링(상한 {@code maxPoolSize}).
     */
    private static List<GuideRecommendRequest.GuideCandidateDto> applyPoolSampling(
            List<GuideRecommendRequest.GuideCandidateDto> full,
            LocalGuestAiProperties.CandidatePoolSettings pool
    ) {
        int cap = Math.max(1, pool.getMaxPoolSize());
        if (full.size() <= cap) {
            return full;
        }

        double ratio = Math.min(1.0d, Math.max(0.0d, pool.getColdStartReserveRatio()));
        List<GuideRecommendRequest.GuideCandidateDto> cold = new ArrayList<>();
        List<GuideRecommendRequest.GuideCandidateDto> warm = new ArrayList<>();
        for (GuideRecommendRequest.GuideCandidateDto d : full) {
            if (isColdPoolCandidate(d)) {
                cold.add(d);
            } else {
                warm.add(d);
            }
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        shuffleInPlace(cold, rnd);
        shuffleInPlace(warm, rnd);

        int coldTarget = (int) Math.ceil(cap * ratio);
        coldTarget = Math.min(coldTarget, cold.size());
        coldTarget = Math.min(coldTarget, cap);
        int warmTarget = cap - coldTarget;
        if (warmTarget > warm.size()) {
            warmTarget = warm.size();
            coldTarget = Math.min(cap - warmTarget, cold.size());
        }

        List<GuideRecommendRequest.GuideCandidateDto> out = new ArrayList<>(cap);
        for (int i = 0; i < coldTarget; i++) {
            out.add(cold.get(i));
        }
        for (int i = 0; i < warmTarget; i++) {
            out.add(warm.get(i));
        }
        if (out.size() < cap) {
            Set<Long> ids = out.stream()
                    .map(GuideRecommendRequest.GuideCandidateDto::getGuideId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (GuideRecommendRequest.GuideCandidateDto d : full) {
                if (out.size() >= cap) {
                    break;
                }
                Long id = d.getGuideId();
                if (id != null && ids.contains(id)) {
                    continue;
                }
                out.add(d);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        log.debug("[AI_POOL] sampled {} from {} (coldReserveRatio={})", out.size(), full.size(), ratio);
        return out;
    }

    private static boolean isColdPoolCandidate(GuideRecommendRequest.GuideCandidateDto d) {
        int rc = d.getReviewCount() == null ? 0 : d.getReviewCount();
        if (rc > 0) {
            return false;
        }
        if (nz(d.getApprovedRefundCount()) > 0) {
            return false;
        }
        if (nz(d.getMatchRequestCount()) + nz(d.getProgressedMatchCount()) + nz(d.getChatStartCount()) > 0) {
            return false;
        }
        return true;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static void shuffleInPlace(List<?> list, ThreadLocalRandom rnd) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    private record PoolCacheEntry(List<GuideRecommendRequest.GuideCandidateDto> candidates, long expiresAtNanos) {
        boolean isExpired() {
            return System.nanoTime() > expiresAtNanos;
        }
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
                .priceMinWon(d.getPriceMinWon())
                .priceMaxWon(d.getPriceMaxWon())
                .priceScope(d.getPriceScope())
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
