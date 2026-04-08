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
            Map.entry("인천", Set.of("서울", "김포")),
            Map.entry("수원", Set.of("서울", "용인", "화성")),
            Map.entry("제주", Set.of()),
            Map.entry("포항", Set.of("경주", "울산")),
            Map.entry("대구", Set.of("경산", "구미", "안동", "포항")),
            Map.entry("전주", Set.of("군산", "익산", "김제", "완주")),
            Map.entry("군산", Set.of("전주", "익산")),
            Map.entry("여수", Set.of("순천", "광양", "보성")),
            Map.entry("순천", Set.of("여수", "광양")),
            Map.entry("춘천", Set.of("홍천", "인제", "가평")),
            Map.entry("통영", Set.of("거제", "진주", "사천")),
            Map.entry("거제", Set.of("통영", "부산", "창원")),
            Map.entry("목포", Set.of("해남", "신안")),
            Map.entry("태안", Set.of("당진", "서산", "보령")),
            Map.entry("창원", Set.of("부산", "거제", "김해"))
    );

    public static Set<String> neighbors(String region) {
        if (region == null || region.isBlank()) {
            return Set.of();
        }
        return NEIGHBORS.getOrDefault(region.trim(), Set.of());
    }
}
