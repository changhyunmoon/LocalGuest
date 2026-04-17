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

    // 전체 리뷰 조회
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ){
        return ResponseEntity.ok(reviewService.findAll(pageable));
    }

    /** 마이페이지 등: 로그인한 작성자 본인의 리뷰만 조회 */
    @GetMapping("/me")
    public ResponseEntity<Page<ReviewResponse>> getMyReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.listMyReviews(pageable));
    }

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody ReviewRequest request) {
        reviewService.saveReview(request);
        return ResponseEntity.ok("리뷰 등록 완료!");
    }

    // 리뷰 수정 24시간 내에만 가능
    @PostMapping("/{reviewId}/update")
    public ResponseEntity<Void> updateReview
            (@PathVariable Long reviewId, @Valid @RequestBody ReviewUpdateRequest request)
    {
        reviewService.updateReview(reviewId, request);
        return ResponseEntity.ok().build();
    }

    // 리뷰 삭제
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    // 특정 가이드의 리뷰 조회
    @GetMapping("/guide/{guideId}")
    public ResponseEntity<Page<ReviewResponse>> getReviews
    (@PathVariable Long guideId, @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsByGuide(guideId, pageable));
    }


}
