package com.team6.domain.review.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.review.dto.reqeust.ReviewRequest;
import com.team6.domain.review.dto.reqeust.ReviewUpdateRequest;
import com.team6.domain.review.dto.response.ReviewResponse;
import com.team6.domain.review.entity.Review;
import com.team6.domain.review.repository.ReviewRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveReview(ReviewRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if(reviewRepository.existsByMemberAndGuideId(member, request.getGuideId())) {
            throw new IllegalStateException("이미 이 가이드에 대한 리뷰를 작성하셨습니다. ");
        }

        if(member.getStatus() == Status.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원은 서비스를 이용할 수 없습니다.");
        }

        Review review = Review.builder()
                .rating(request.getRating())
                .content(request.getContent())
                .member(member)
                .guideId(request.getGuideId())
                .build();
        reviewRepository.save(review);
    }

    // 리뷰 수정(24시간 이내 1번만 수정 가능)
    @Transactional
    public void updateReview(Long reviewId, ReviewUpdateRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(()-> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        // 본인 확인
        if(!review.getMember().getEmail().equals(email)) {
            throw new IllegalStateException("본인이 작성한 리뷰만 수정할 수 있습니다.");
        }

        // 수정 기간 확인
        if(review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1))) {
            throw new IllegalStateException("리뷰 수정 가능 시간(24시간)이 지났습니다.");
        }

        review.update(request.getRating(), request.getContent());
    }

    // 모든 리뷰 조회
    @Transactional(readOnly = true)
    public Page<ReviewResponse> findAll(Pageable pageable) {
        return reviewRepository.findAll(pageable)
                .map(ReviewResponse::new);
    }

    // 특정 가이드에 대한 모든 리뷰 페이징기법으로 조회
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByGuide(Long guideId, Pageable pageable) {
        return reviewRepository.findAllByGuideId(guideId, pageable)
                .map(ReviewResponse::new);
    }
}
