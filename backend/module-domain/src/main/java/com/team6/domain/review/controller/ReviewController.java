package com.team6.domain.review.controller;

import com.team6.domain.review.dto.reqeust.ReviewRequest;
import com.team6.domain.review.dto.response.ReviewResponse;
import com.team6.domain.review.entity.Review;
import com.team6.domain.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    public final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ReviewRequest request) {
        reviewService.saveReview(request);
        return ResponseEntity.ok("리뷰 등록 완료!");
    }

    // AI담당자는 데이터를 긁어가십시여
    @GetMapping("/guide/{guideId}")
    public ResponseEntity<List<ReviewResponse>> getReviews(@PathVariable Long guideId) {
        return ResponseEntity.ok(reviewService.getReviewsByGuide(guideId));
    }
}
