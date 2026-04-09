package com.team6.domain.review.dto.response;

import com.team6.domain.review.entity.Review;
import lombok.Getter;

@Getter
public class ReviewResponse {
    private Long id;
    private Integer rating;
    private String content;

    public ReviewResponse(Review review) {
        this.id = review.getId();
        this.rating = review.getRating();
        this.content = review.getContent();
    }
}
