package com.team6.module.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(name = "GuideRecommendRequest", description = "파싱 결과 또는 직접 구성한 추천 요청(내부/테스트용)")
@Getter
@Builder(toBuilder = true)
public class GuideRecommendRequest {
    private String region;
    private String travelStyle;
    private String budgetLevel;
    /** 예산 하한(원). */
    private Integer budgetMinWon;
    /** 예산 상한(원). */
    private Integer budgetMaxWon;
    /** 예산 단위/범위 해석 힌트(per_person/total/per_day). */
    private String budgetScope;
    private String companionType;
    private List<String> activityTags;
    /** 파서가 강한 의도로 분류한 활동 태그(부분집합). */
    private List<String> requiredActivityTags;
    /** 파서가 약한 선호로 분류한 활동 태그(부분집합). */
    private List<String> niceToHaveActivityTags;
    /** 파서가 강한 의도로 분류한 언어(부분집합). */
    private List<String> requiredLanguages;
    /** 파서가 약한 선호로 분류한 언어(부분집합). */
    private List<String> niceToHaveLanguages;
    private List<String> preferredLanguages;
    /** 사용자가 '근처/인접도 OK'를 명시했는지(정확 지역 후보가 충분해도 인접을 포함). */
    private Boolean allowAdjacentRegion;
    /** 예산 힌트가 강한 의도(꼭/필수)인지. */
    private Boolean strictBudget;
    private Integer headcount;
    private Integer durationDays;
    private List<String> excludedActivityTags;
    /** 지역 제외(예: "서울 말고"). */
    private List<String> excludedRegions;
    /** 여행 스타일 제외(예: "힐링은 싫고"). */
    private List<String> excludedTravelStyles;
    /** 언어 제외(예: "영어는 원치 않아"). */
    private List<String> excludedLanguages;
    /**
     * 하드 제외가 아니라, 가이드가 해당 활동을 강하게 전문으로 내세울 때 점수를 깎는(soft) 태그.
     */
    private List<String> softPenaltyActivityTags;
    /**
     * LLM 전용: 가이드에게 보여줄 불릿 한 줄(반말). OpenAPI·일반 JSON 응답에는 실리지 않는다.
     */
    @JsonIgnore
    private List<String> llmGuideBullets;
    /**
     * LLM 전용: 특별 요청 문단. OpenAPI·일반 JSON 응답에는 실리지 않는다.
     */
    @JsonIgnore
    private String llmSpecialRequests;
    private Integer topN;
    private List<GuideCandidateDto> guideCandidates;

    /**
     * 파서 전용: 선호/제외 충돌 정리 등 내부 안내 코드. 직렬화·OpenAPI에는 노출하지 않는다.
     */
    @JsonIgnore
    private List<String> parserNoticeCodes;

    /**
     * 희망 투어 기간 내 예약 가능한 날짜 수(가이드별). 랭킹 가산에만 쓰이며 JSON API에는 실리지 않는다.
     */
    @JsonIgnore
    private Map<Long, Integer> availabilityDayCountByGuideId;

    /**
     * 희망 투어 기간 내 "최대 연속 가능일"(가이드별). 랭킹 가산에만 쓰이며 JSON API에는 실리지 않는다.
     */
    @JsonIgnore
    private Map<Long, Integer> availabilityMaxConsecutiveDaysByGuideId;

    @Schema(name = "GuideCandidateDto", description = "추천 후보 가이드 프로필")
    @Getter
    @Builder
    public static class GuideCandidateDto {
        private Long guideId;
        private String guideName;
        private String region;
        private String guideStyle;
        private String priceLevel;
        @Schema(description = "가격 하한(원). 범위 기반 예산 매칭에 사용(선택)")
        private Integer priceMinWon;
        @Schema(description = "가격 상한(원). 범위 기반 예산 매칭에 사용(선택)")
        private Integer priceMaxWon;
        @Schema(description = "가격 단위/범위 해석(per_person/total/per_day). 범위 기반 예산 매칭에 사용(선택)")
        private String priceScope;
        private List<String> specialtyTags;
        private List<String> languages;
        /** 도메인 집계: 평균 평점(없으면 null — 감점 미적용). */
        @Schema(description = "가이드 평균 평점(리뷰 집계). 미전달 시 피드백 감점에 쓰이지 않음")
        private BigDecimal averageRating;
        @Schema(description = "리뷰 수. averageRating과 함께 쓰여 저평점 감점 여부를 판단")
        private Integer reviewCount;
        /** 승인된 환불 건수(가이드 기준). 미전달 시 환불 감점 미적용. */
        @Schema(description = "승인(APPROVED) 환불 건수. 집계해 전달하면 룰 기반 감점에 반영")
        private Integer approvedRefundCount;
        @Schema(description = "추천 이후 실제 매칭 요청으로 이어진 횟수. 행동 기반 보정에 활용")
        private Integer matchRequestCount;
        @Schema(description = "수락/진행 단계까지 이어진 매칭 횟수. 전환 품질 신호로 활용")
        private Integer progressedMatchCount;
        @Schema(description = "가이드가 참여한 채팅방 시작 횟수. 추천 이후 실제 대화 연결 신호로 활용")
        private Integer chatStartCount;

        @Schema(description = "프로필 대표 이미지 URL(카드 썸네일)")
        private String representativeImageUrl;

        @Schema(description = "공개 피드 이미지 URL 목록(최신순·개수 상한은 서버/클라이언트 합의)")
        private List<String> publicFeedThumbnailUrls;

        @Schema(description = "추천 카드 클릭 집계(관심/탐색 신호). 미전달 시 미반영")
        private Integer recommendClickCount;

        @Schema(description = "포지션(노출 순위) 편향을 보정한 클릭 신호 점수. 미전달 시 미반영")
        private Integer recommendClickDebiasedScore;

        @Schema(description = "동일 세션 내 최근 추천 노출 횟수(반복 추천 완화용). 미전달 시 미반영")
        private Integer recommendExposureCount;

        /**
         * LLM 순위용: 소개(bio+localStory) 스니펫. OpenAPI·JSON에 실리지 않는다.
         */
        @JsonIgnore
        private String llmIntroSnippet;
        /**
         * LLM 순위용: 공개 피드 본문(최신순, 각 글은 서버에서 길이 상한 적용). 비어 있을 수 있음.
         */
        @JsonIgnore
        private List<String> llmFeedBodiesNewestFirst;
        /** LLM 순위용: 경력 요약 스니펫. */
        @JsonIgnore
        private String llmCareerSnippet;
        /** LLM 순위용: 디폴트 코스 스니펫. */
        @JsonIgnore
        private String llmDefaultCourseSnippet;
        /** LLM 순위용: 삭제 제외 공개 피드 개수. */
        @JsonIgnore
        private Integer publicFeedCount;
    }
}
