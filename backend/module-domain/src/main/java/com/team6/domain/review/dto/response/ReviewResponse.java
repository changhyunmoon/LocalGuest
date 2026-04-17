package com.team6.domain.review.dto.response;

import com.team6.domain.review.entity.Review;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReviewResponse {
    private Long id;
    private Long guideId;
    private Long matchRequestId;
    private Integer rating;
    private String content;
    private String writeNickname;
    private LocalDateTime createdAt;

    public ReviewResponse (Review review) {
        this.id = review.getId();
        this.guideId = review.getGuideId();
        this.matchRequestId = review.getMatchRequestId();
        this.rating = review.getRating();
        this.content = review.getContent();
        this.writeNickname = review.getMember().getNickname();
        this.createdAt = review.getCreatedAt();
    }
}
