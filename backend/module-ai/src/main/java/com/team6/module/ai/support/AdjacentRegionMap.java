package com.team6.module.ai.support;

import java.util.Map;
import java.util.Set;

/**
 * MVP용 인접 지역 맵(코드 상수). 추후 DB/운영 설정으로 이전 가능.
 */
public final class AdjacentRegionMap {

    private AdjacentRegionMap() {
    }

    private static final Map<String, Set<String>> NEIGHBORS = Map.ofEntries(
            Map.entry("강릉", Set.of("속초", "동해", "삼척", "양양")),
            Map.entry("속초", Set.of("강릉", "양양", "고성")),
            Map.entry("동해", Set.of("강릉", "삼척")),
            Map.entry("삼척", Set.of("강릉", "동해")),
            Map.entry("양양", Set.of("강릉", "속초")),
            Map.entry("부산", Set.of("경주", "울산", "거제", "창원")),
            Map.entry("경주", Set.of("부산", "포항", "경산")),
            Map.entry("울산", Set.of("부산", "경주")),
            Map.entry("서울", Set.of("인천", "가평", "수원", "파주")),
            Map.entry("인천", Set.of("서울")),
            Map.entry("수원", Set.of("서울", "용인")),
            Map.entry("제주", Set.of()),
            Map.entry("포항", Set.of("경주", "울산"))
    );

    public static Set<String> neighbors(String region) {
        if (region == null || region.isBlank()) {
            return Set.of();
        }
        return NEIGHBORS.getOrDefault(region.trim(), Set.of());
    }
}
