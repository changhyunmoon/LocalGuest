package com.team6.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 추천 모듈 설정({@code localguest.ai.*}).
 * <p><b>정책 버전과의 정합</b>: API 응답·로그·Micrometer에 실리는
 * {@link com.team6.module.ai.support.AiRecommendationTuning#POLICY_VERSION}은
 * 룰 스코어·다양성·Reason 계약이 바뀔 때마다 올린다. DB에서 후보를 채울 때(api-server·integration 모듈의
 * DB 후보 제공자) 풀 캐시 키 접두에도 동일 문자열을 쓰므로,
 * <b>스코어링·다양성 의미를 바꾸는 배포</b>에서는 버전을 함께 올려 캐시·지표·응답이 어긋나지 않게 한다.
 * <p><b>블록별 역할</b>
 * <ul>
 *   <li>{@link #getCandidatePool()} — 후보 <b>조회 상한·샘플링·캐시 TTL·제외 ID</b>. 스코어 가중치와는 별개이나,
 *       풀만 바꿔도 캐시 키에 반영된다({@code maxPoolSize}, {@code maxFetchSize} 등).</li>
 *   <li>{@link #getScoringPolicy()} — 룰 점수·피드백·콤보·폴백 임계({@code ScoringPolicySnapshot}).</li>
 *   <li>{@link #getDiversityRerank()} — Top-N 다양성 패널티(λ·유사도 가중).</li>
 *   <li>{@link #getParser()}·{@link #getAdjacentRegions()} — 파싱·인접 지역(후보 확장·스코어 모두에 영향).</li>
 * </ul>
 * 운영 튜닝 시 세 블록을 같은 배포·같은 {@code POLICY_VERSION} 전제로 맞추는 것을 권장한다.
 * <p>
 * {@code adjacentRegions}에 지역 키가 있으면 해당 목록을 사용하고,
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

    /**
     * DB 후보 풀 조회·샘플링·캐시({@code localguest.ai.candidate-pool}).
     * 캐시 키 구성은 integration 모듈의 DB 후보 제공자 구현을 참고한다.
     */
    private CandidatePoolSettings candidatePool = new CandidatePoolSettings();

    /**
     * 룰 스코어·피드백·조합 보너스({@code localguest.ai.scoring-policy}) → {@link ScoringPolicySnapshot}.
     */
    private ScoringPolicySettings scoringPolicy = new ScoringPolicySettings();

    /**
     * Top-N 다양성 패널티(λ·유사도 가중, {@code localguest.ai.diversity-rerank}) → {@link DiversityRerankSnapshot}.
     */
    private DiversityRerankSettings diversityRerank = new DiversityRerankSettings();

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

    @Getter
    @Setter
    public static class CandidatePoolSettings {
        /**
         * 스코어링에 넘기는 후보 최대 수(샘플링 후 상한).
         * DB 후보 풀 캐시 키에 포함된다.
         */
        private int maxPoolSize = 80;
        /**
         * DB에서 가져오는 상한(정확 일치 + 부분 일치 단계에서 사용).
         * 캐시 키에 포함된다.
         */
        private int maxFetchSize = 200;
        /**
         * 콜드스타트 후보(리뷰·행동 신호 거의 없음)를 풀에 확보하려는 최소 비율(0~1).
         * 캐시 키에 포함된다.
         */
        private double coldStartReserveRatio = 0.12d;
        /**
         * 후보 풀 캐시 TTL(초). 0 이하면 비활성.
         * TTL 값은 키에 넣지 않고 만료 시각으로만 관리한다.
         */
        private int poolCacheTtlSeconds = 45;
        /**
         * AI 추천 풀에서 제외할 가이드 프로필 ID(운영용).
         * 캐시 키에 포함된다.
         */
        private List<Long> excludedGuideIds = new ArrayList<>();
    }
}
