package com.team6.domain.review.service;

import com.team6.domain.guide.dto.request.UpdateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideRatingRequest;
import com.team6.domain.guide.service.GuideProfileService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.review.dto.request.ReviewRequest;
import com.team6.domain.review.dto.request.ReviewUpdateRequest;
import com.team6.domain.review.dto.response.ReviewResponse;
import com.team6.domain.review.entity.Review;
import com.team6.domain.review.repository.ReviewRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MemberRepository memberRepository;
    private final GuideProfileService guideProfileService;

    // 리뷰 저장
    @Transactional
    public void saveReview(ReviewRequest request) {
        Member member = getCurrentMember();

        if(reviewRepository.existsByMatchRequestId(request.getMatchRequestId())) {
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
                .matchRequestId(request.getMatchRequestId())
                .build();
        reviewRepository.save(review);

        syncGuideRating(request.getGuideId());
    }

    // 리뷰 수정 (24시간 이내만 수정 가능)
    @Transactional
    public void updateReview(Long reviewId, ReviewUpdateRequest request) {
        Member member = getCurrentMember();

        Review review = reviewRepository.findByIdAndMember(reviewId, member)
                .orElseThrow(()-> new IllegalArgumentException("해당 리뷰를 찾을 수 없습니다."));

        // 수정 기간 확인
        if(review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1))) {
            throw new IllegalStateException("리뷰 수정 가능 시간(24시간)이 지났습니다.");
        }

        review.update(request.getRating(), request.getContent());
        syncGuideRating(review.getGuideId());
    }

    // 모든 리뷰 조회
    @Transactional(readOnly = true)
    public Page<ReviewResponse> findAll(Pageable pageable) {
        return reviewRepository.findAllByDeletedFalse(pageable)
                .map(ReviewResponse::new);
    }

    // 특정 가이드에 대한 모든 리뷰 페이징기법으로 조회
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByGuide(Long guideId, Pageable pageable) {
        return reviewRepository.findAllByGuideIdAndDeletedFalse(guideId, pageable)
                .map(ReviewResponse::new);
    }

    // 리뷰 삭제
    @Transactional
    public void deleteReview(Long reviewId) {
        Member member = getCurrentMember();

        Review review = reviewRepository.findByIdAndMember(reviewId, member)
                .orElseThrow(() -> new IllegalArgumentException(("리뷰를 찾을 수 없습니다. ")));

        review.delete();
        syncGuideRating(review.getGuideId());
    }

    private void syncGuideRating(Long guideId) {
        List<Object[]> result = reviewRepository.getRatingAndCountByGuideId(guideId);

        Object[] stats = result.isEmpty()?new Object[]{null, 0L} : result.get(0);

        Double rawAvg = stats[0] != null ? (Double) stats[0] : 0.0;
        Long rawCount = stats[1] != null ? (Long) stats[1] : 0L;

        BigDecimal averageRating = BigDecimal.valueOf(rawAvg).setScale(1, RoundingMode.HALF_UP);
        Integer reviewCount = rawCount.intValue();

        UpdateGuideRatingRequest ratingRequest = createRatingRequest(averageRating, reviewCount);

        guideProfileService.updateRating(guideId, ratingRequest);
    }

    private UpdateGuideRatingRequest createRatingRequest(BigDecimal averageRating, Integer reviewCount) {
        return UpdateGuideRatingRequest.builder()
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .build();
    }

    /**
     * 현재 로그인한 사용자의 Email과 Role을 이용해 Member 엔티티를 조회하는 공통 메소드
     */
    private Member getCurrentMember() {
        String email = SecurityUtil.getCurrentUserEmail();
        String roleString = SecurityUtil.getCurrentUserRoleString();

        // "ROLE_" 접두사 제거 후 Enum 변환
        Role role = Role.valueOf(roleString.replace("ROLE_", ""));

        return memberRepository.findByEmailAndRole(email, role)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }
}
