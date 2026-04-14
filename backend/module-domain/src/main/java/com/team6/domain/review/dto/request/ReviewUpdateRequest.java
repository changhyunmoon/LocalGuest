package com.team6.domain.review.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewUpdateRequest {

    @NotNull(message = "별점은 필수입니다. ")
    @Min(value = 1, message = "별점은 최소 1점 이상이어야 합니다. ")
    @Max(value = 5, message = "별점은 최대 5점 이하여야 합니다. ")
    private Integer rating;

    @NotBlank(message = "리뷰 내용을 입력해주세요. ")
    @Size(min = 10, max = 500, message = "리뷰는 10자 이상 500자 이하로 작성해주세요. ")
    private String content;
}
