package com.team6.module.ai.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class GuideAiProfile {
    private Long guideId;
    private String guideName;
    private String region;
    private String guideStyle;
    private String priceLevel;
    private Integer priceMinWon;
    private Integer priceMaxWon;
    private String priceScope;
    private List<String> specialtyTags;
    private List<String> languages;
    /** 평균 평점(도메인). null이면 평점 감점 없음. */
    private BigDecimal averageRating;
    private Integer reviewCount;
    /** 승인 환불 건수. null이면 환불 감점 없음. */
    private Integer approvedRefundCount;
    /** 추천 이후 매칭 요청이 실제로 생성된 횟수. */
    private Integer matchRequestCount;
    /** 수락/진행 단계까지 이어진 매칭 횟수. */
    private Integer progressedMatchCount;
    /** 실제 채팅방 시작 횟수. */
    private Integer chatStartCount;

    /** 추천 카드 클릭(관심/탐색) 집계. null이면 미반영. */
    private Integer recommendClickCount;

    /** 동일 세션 내 최근 추천 노출 횟수(반복 추천 완화). null이면 미반영. */
    private Integer recommendExposureCount;

    /** 프로필 대표 이미지 URL. */
    private String representativeImageUrl;

    /** 공개 피드 썸네일 URL(최신순, 상한 적용된 목록). */
    private List<String> publicFeedThumbnailUrls;
}
