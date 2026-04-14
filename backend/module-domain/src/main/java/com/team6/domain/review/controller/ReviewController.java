package com.team6.domain.review.controller;

import com.team6.domain.review.dto.request.ReviewRequest;
import com.team6.domain.review.dto.request.ReviewUpdateRequest;
import com.team6.domain.review.dto.response.ReviewResponse;
import com.team6.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ){
        return ResponseEntity.ok(reviewService.findAll(pageable));
    }

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody ReviewRequest request) {
        reviewService.saveReview(request);
        return ResponseEntity.ok("리뷰 등록 완료!");
    }

    @PostMapping("/{reviewId}/update")
    public ResponseEntity<Void> updateReview
            (@PathVariable Long reviewId, @Valid @RequestBody ReviewUpdateRequest request)
    {
        reviewService.updateReview(reviewId, request);
        return ResponseEntity.ok().build();
    }

    // AI담당자는 데이터를 긁어가십시여
    @GetMapping("/guide/{guideId}")
    public ResponseEntity<Page<ReviewResponse>> getReviews
    (@PathVariable Long guideId, @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsByGuide(guideId, pageable));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}
