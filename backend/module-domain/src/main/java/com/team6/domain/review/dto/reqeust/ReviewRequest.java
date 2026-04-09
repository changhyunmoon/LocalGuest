package com.team6.domain.review.dto.reqeust;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewRequest {
    private Long guideId;
    private Integer rating;
    private String content;
}
