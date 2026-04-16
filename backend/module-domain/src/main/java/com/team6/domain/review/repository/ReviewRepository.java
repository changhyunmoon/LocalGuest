package com.team6.domain.review.repository;

import com.team6.domain.member.entity.Member;
import com.team6.domain.review.entity.Review;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findAllByGuideIdAndDeletedFalse(Long guideId, Pageable pageable);

    @EntityGraph(attributePaths = {"member"})
    Page<Review> findAllByDeletedFalse(Pageable pageable);

    boolean existsByMatchRequestId(Long matchedRequestedId);

    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.guideId = :guideId")
    List<Object[]> getRatingAndCountByGuideId(@Param("guidId") Long guideId);

    Optional<Review> findByIdAndMember(Long id, Member member);



}
