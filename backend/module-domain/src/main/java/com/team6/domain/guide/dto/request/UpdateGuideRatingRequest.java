package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 가이드 평점 갱신 요청 DTO — review 도메인이 리뷰 작성/삭제 후 호출
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGuideRatingRequest {

    @NotNull
    private BigDecimal averageRating; // 갱신할 평균 평점

    @NotNull
    @Min(0)
    private Integer reviewCount; // 갱신할 리뷰 수
}
