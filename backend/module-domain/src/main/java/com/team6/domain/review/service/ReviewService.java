package com.team6.domain.review.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.review.dto.reqeust.ReviewRequest;
import com.team6.domain.review.dto.response.ReviewResponse;
import com.team6.domain.review.entity.Review;
import com.team6.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.security.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveReivew(ReviewRequest request) {
        String email = SecurityUtil.getCurrentUserEmail();
        Member guest = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. "));
        Member guide = memberRepository.findById(request.getGuideId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. "));

        Review review = Review.builder()
                .rating(request.getRating())
                .content(request.getContent())
                .guest(guest)
                .guide(guide)
                .build();
        reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByGuide(Long guideId) {
        return reviewRepository.findAllByGuideId(guideId).stream()
                .map(ReviewResponse::new)
                .collect(Collectors.toList());
    }
}
