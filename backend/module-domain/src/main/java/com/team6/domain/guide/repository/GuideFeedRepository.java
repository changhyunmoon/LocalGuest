package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideFeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 가이드 피드 레포지토리 (guide_feeds 테이블)
 */
public interface GuideFeedRepository extends JpaRepository<GuideFeed, Long> {

    // 특정 가이드의 삭제되지 않은 피드 목록 조회 — 최신순 정렬 (F06-03)
    List<GuideFeed> findByGuideProfile_IdAndIsDeletedFalseOrderByCreatedAtDesc(Long guideId);

    // 피드 단건 조회 — 삭제되지 않은 피드만 (F06-03)
    Optional<GuideFeed> findByIdAndIsDeletedFalse(Long id);
}
