package com.team6.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    }
}
