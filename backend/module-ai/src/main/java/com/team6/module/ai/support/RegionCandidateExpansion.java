package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지역 후보가 부족할 때 인접 지역 가이드를 포함해 풀을 넓힌다.
 */
public final class RegionCandidateExpansion {

    private RegionCandidateExpansion() {
    }

    public record Result(
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            boolean expansionUsed,
            int exactCount
    ) {
    }

    public static Result apply(List<GuideRecommendRequest.GuideCandidateDto> all, String region) {
        return apply(all, region, AdjacentRegionMap::neighbors);
    }

    /**
     * @param adjacentNeighbors 지역명 → 인접 지역 집합 (테스트·YAML 주입용)
     */
    public static Result apply(
            List<GuideRecommendRequest.GuideCandidateDto> all,
            String region,
            Function<String, Set<String>> adjacentNeighbors
    ) {
        return apply(all, region, adjacentNeighbors, false);
    }

    /**
     * @param allowAdjacentEvenIfExactEnough 사용자가 '근처도/인접도 OK'를 명시한 경우 등,
     *                                      정확 지역 후보가 충분해도 인접 지역을 포함해 풀을 넓힌다.
     */
    public static Result apply(
            List<GuideRecommendRequest.GuideCandidateDto> all,
            String region,
            Function<String, Set<String>> adjacentNeighbors,
            boolean allowAdjacentEvenIfExactEnough
    ) {
        List<GuideRecommendRequest.GuideCandidateDto> pool =
                all == null ? List.of() : new ArrayList<>(all);

        if (region == null || region.isBlank()) {
            return new Result(pool, false, 0);
        }

        String r = region.trim();
        List<GuideRecommendRequest.GuideCandidateDto> exact = pool.stream()
                .filter(Objects::nonNull)
                .filter(g -> regionEquals(g.getRegion(), r))
                .collect(Collectors.toList());

        if (!allowAdjacentEvenIfExactEnough && exact.size() >= AiRecommendationTuning.MIN_EXACT_REGION_CANDIDATES) {
            return new Result(exact, false, exact.size());
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add(r);
        allowed.addAll(adjacentNeighbors.apply(r));

        List<GuideRecommendRequest.GuideCandidateDto> wider = pool.stream()
                .filter(Objects::nonNull)
                .filter(g -> g.getRegion() != null && allowed.stream()
                        .anyMatch(a -> regionEquals(g.getRegion(), a)))
                .collect(Collectors.toList());

        if (wider.isEmpty()) {
            return new Result(pool, false, exact.size());
        }

        boolean expansionUsed = wider.stream().anyMatch(g -> !regionEquals(g.getRegion(), r));
        return new Result(wider, expansionUsed, exact.size());
    }

    private static boolean regionEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
