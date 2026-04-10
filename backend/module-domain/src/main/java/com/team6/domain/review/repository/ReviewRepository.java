package com.team6.domain.review.repository;

import com.team6.domain.member.entity.Member;
import com.team6.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findAllByGuideId(Long guideId , Pageable pageable);

    boolean existsByMemberAndGuideId(Member member, Long guideId);
}
