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
    private List<String> specialtyTags;
    private List<String> languages;
    /** 평균 평점(도메인). null이면 평점 감점 없음. */
    private BigDecimal averageRating;
    private Integer reviewCount;
    /** 승인 환불 건수. null이면 환불 감점 없음. */
    private Integer approvedRefundCount;
}