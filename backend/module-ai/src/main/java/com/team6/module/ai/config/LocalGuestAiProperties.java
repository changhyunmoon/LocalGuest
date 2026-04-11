package com.team6.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 추천 모듈 설정. {@code adjacentRegions}에 지역 키가 있으면 해당 목록을 사용하고,
 * 없으면 {@link com.team6.module.ai.support.AdjacentRegionMap} 내장 값을 사용한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "localguest.ai")
public class LocalGuestAiProperties {

    /**
     * 지역명 → 인접 지역 목록 (예: 강릉: [속초, 동해]). 비우면 전부 내장 맵.
     */
    private Map<String, List<String>> adjacentRegions = new LinkedHashMap<>();

    /**
     * 파서 보강(태그 동의어). 비우면 내장 {@link com.team6.module.ai.parser.KeywordNormalizer}만 사용.
     */
    private ParserSettings parser = new ParserSettings();

    @Getter
    @Setter
    public static class ParserSettings {
        /**
         * 입력 표현 → 정규 태그 (예: 커스텀별칭 → 카페).
         */
        private Map<String, String> tagSynonyms = new LinkedHashMap<>();
        /** 비어 있으면 내장 soft 부정(힘듦·고난이도 등) 키워드만 사용. */
        private List<String> softPenaltyHeavyHints = new ArrayList<>();
        /** 비어 있으면 내장 등산/트레킹 맥락만 사용. */
        private List<String> softPenaltyHikingContext = new ArrayList<>();
        private List<String> softPenaltyNightlifeNoiseHints = new ArrayList<>();
        private List<String> softPenaltyShoppingCrowdHints = new ArrayList<>();
        private List<String> softPenaltyShoppingActivityContext = new ArrayList<>();
        /** 제외 의도(빼고·싫어 등). 비어 있으면 내장. */
        private List<String> exclusionIntentKeywords = new ArrayList<>();
        /**
         * 프롬프트에 부분 문자열로 등장하는 표기 → canonical 지역명(한글).
         * 키는 소문자로 정규화되어 매칭된다. 내장 영문·로마자 맵 이후에 병합되며 동일 키는 YAML이 덮어쓴다.
         */
        private Map<String, String> regionAliases = new LinkedHashMap<>();
    }
}
